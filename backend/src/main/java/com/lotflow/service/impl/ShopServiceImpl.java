package com.lotflow.service.impl;

import com.lotflow.service.IdempotencyService;
import com.lotflow.service.OrderService;
import com.lotflow.service.OutboxService;
import com.lotflow.service.ShopService;

import com.lotflow.common.BusinessException;
import com.lotflow.common.IdempotencyKey;
import com.lotflow.common.ResultCode;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.OrderDO;
import com.lotflow.entity.enums.DomainEnums.ListingStatus;
import com.lotflow.entity.enums.DomainEnums.SaleChannelStatus;
import com.lotflow.mapper.DatabaseTimeMapper;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.mapper.SaleChannelMapper;
import com.lotflow.vo.OrderVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/** 商城一口价下单。 */
@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final ListingMapper listingMapper;
    private final SaleChannelMapper channelMapper;
    private final DatabaseTimeMapper timeMapper;
    private final OrderService orderService;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderVO purchase(long buyerId, long listingId, String rawIdempotencyKey) {
        String key = IdempotencyKey.require(rawIdempotencyKey);
        var claim = idempotencyService.claim("shop.order", key,
                Map.of("listingId", listingId, "buyerId", buyerId));
        if (claim.completed()) {
            // 同一幂等键重放时返回首次订单，不会让同一库存重复创建第二个商城订单。
            return orderService.get(buyerId, false, claim.resourceId());
        }
        ListingDO listing = listingMapper.selectByIdForUpdate(listingId);
        if (listing == null || listing.getStatus() != ListingStatus.SHOP) {
            throw new BusinessException(ResultCode.CONFLICT, "商品不在商城在售状态");
        }
        var channel = channelMapper.selectActiveForUpdate(listingId);
        OffsetDateTime now = timeMapper.now();
        if (channel == null || channel.getStatus() != SaleChannelStatus.ACTIVE
                || !now.isBefore(channel.getEndsAt())) {
            // 商城库存以渠道行状态和数据库时间判断；已售、已下架或到期的商品不能再下单。
            throw new BusinessException(ResultCode.CONFLICT, "商品已下架或售出");
        }
        OrderDO order = orderService.createRetailOrder(listing, buyerId, channel.getPrice(),
                "SHOP", now.plusHours(24));
        // 订单创建、渠道 SOLD 标记和幂等完成状态同事务提交，保证单库存只能形成一个有效订单。
        channel.setStatus(SaleChannelStatus.SOLD);
        channel.setOrderId(order.getId());
        channelMapper.updateById(channel);
        idempotencyService.complete(claim, order.getId());
        outboxService.append("shop.sold", "listing", listingId,
                Map.of("orderId", order.getId(), "buyerId", buyerId));
        return OrderVO.from(order);
    }
}
