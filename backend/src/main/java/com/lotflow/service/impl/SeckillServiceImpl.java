package com.lotflow.service.impl;

import com.lotflow.common.AfterCommit;
import com.lotflow.common.BusinessException;
import com.lotflow.common.IdempotencyKey;
import com.lotflow.common.ResultCode;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.OrderDO;
import com.lotflow.entity.enums.DomainEnums.ListingStatus;
import com.lotflow.entity.enums.DomainEnums.ReservationStatus;
import com.lotflow.entity.enums.DomainEnums.SaleChannelStatus;
import com.lotflow.mapper.DatabaseTimeMapper;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.mapper.OrderMapper;
import com.lotflow.mapper.SaleChannelMapper;
import com.lotflow.service.IdempotencyService;
import com.lotflow.service.OrderService;
import com.lotflow.service.OutboxService;
import com.lotflow.service.SeckillService;
import com.lotflow.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 秒杀库存预占、订单事实和超时恢复。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "lotflow:seckill:";

    private final ListingMapper listingMapper;
    private final OrderMapper orderMapper;
    private final SaleChannelMapper channelMapper;
    private final DatabaseTimeMapper timeMapper;
    private final OrderService orderService;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;
    private final StringRedisTemplate redisTemplate;

    @Qualifier("seckillReserveScript")
    private final DefaultRedisScript<Long> reserveScript;

    @Qualifier("seckillReleaseScript")
    private final DefaultRedisScript<Long> releaseScript;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderVO purchase(long buyerId, long listingId, String rawIdempotencyKey) {
        String key = IdempotencyKey.require(rawIdempotencyKey);
        var claim = idempotencyService.claim("seckill.order", key,
                Map.of("listingId", listingId, "buyerId", buyerId));
        if (claim.completed()) {
            return orderService.get(buyerId, false, claim.resourceId());
        }

        String redisKey = KEY_PREFIX + listingId;
        reserve(redisKey, key);
        // 数据库事务可能直到方法返回后才提交失败，因此由事务完成回调释放 Redis token，不能只依赖方法体 catch。
        releaseRedisOnRollback(redisKey, key);
        return createReservedOrder(buyerId, listingId, key, claim);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void expireReservation(long orderId) {
        OrderDO snapshot = orderMapper.selectById(orderId);
        if (!isActiveReservation(snapshot)) {
            return;
        }
        OffsetDateTime now = timeMapper.now();
        if (snapshot.getReservationExpiresAt().isAfter(now)) {
            return;
        }
        // 先由订单状态机释放数据库库存，Redis 令牌只在事务提交后删除：
        // 库存恢复回滚时令牌必须先保留，否则 Redis 已放行而数据库仍被占用。
        orderService.processPendingPayment(orderId);
        AfterCommit.run(() -> releaseRedisKey(KEY_PREFIX + snapshot.getListingId(),
                snapshot.getReservationKey()));
    }

    private OrderVO createReservedOrder(long buyerId, long listingId, String key,
                                        IdempotencyService.IdempotencyClaim claim) {
        ListingDO listing = listingMapper.selectByIdForUpdate(listingId);
        OffsetDateTime now = timeMapper.now();
        if (listing == null || listing.getStatus() != ListingStatus.SECKILL) {
            throw new BusinessException(ResultCode.CONFLICT, "商品不在秒杀中");
        }
        var channel = channelMapper.selectActiveForUpdate(listingId);
        if (channel == null || channel.getStatus() != SaleChannelStatus.ACTIVE
                || !now.isBefore(channel.getEndsAt())) {
            throw new BusinessException(ResultCode.CONFLICT, "秒杀已结束");
        }

        OrderDO order = orderService.createSeckillOrder(listing, buyerId, channel.getPrice(),
                now.plus(RESERVATION_TTL), key);

        // 渠道 SOLD、订单预占和幂等结果同事务提交，单库存不会产生第二个有效订单。
        channel.setStatus(SaleChannelStatus.SOLD);
        channel.setOrderId(order.getId());
        channelMapper.updateById(channel);
        idempotencyService.complete(claim, order.getId());
        outboxService.append("seckill.reserved", "listing", listingId,
                Map.of("orderId", order.getId(), "buyerId", buyerId));
        return OrderVO.from(order);
    }

    private void reserve(String redisKey, String token) {
        Long reserved;
        try {
            reserved = redisTemplate.execute(reserveScript, List.of(redisKey),
                    token, Long.toString(RESERVATION_TTL.toMillis()));
        } catch (DataAccessException exception) {
            // Redis Lua 是秒杀流量闸门；不可用时停止抢购，不能降级为直接冲击 PostgreSQL。
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "秒杀暂不可用");
        }
        if (reserved == null || reserved == 0L) {
            throw new BusinessException(ResultCode.CONFLICT, "商品已被其他用户抢到");
        }
    }

    private boolean isActiveReservation(OrderDO order) {
        return order != null && "SECKILL".equals(order.getSourceType())
                && order.getReservationStatus() == ReservationStatus.RESERVED
                && order.getReservationExpiresAt() != null
                && order.getReservationKey() != null;
    }

    private void releaseRedisKey(String redisKey, String token) {
        if (token == null) {
            return;
        }
        try {
            redisTemplate.execute(releaseScript, List.of(redisKey), token);
        } catch (DataAccessException exception) {
            // 数据库已恢复库存，Redis key 最迟由十五分钟 TTL 自动收敛。
            log.warn("seckill redis token release deferred, key={}", redisKey, exception);
        }
    }

    private void releaseRedisOnRollback(String redisKey, String token) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("seckill purchase requires an active transaction");
        }
        // 只有回滚或未知提交结果才释放本次 token；成功提交后渠道已售出，不能再让后续请求抢同一库存。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    releaseRedisKey(redisKey, token);
                }
            }
        });
    }
}
