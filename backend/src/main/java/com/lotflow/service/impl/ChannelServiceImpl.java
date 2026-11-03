package com.lotflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import com.lotflow.dto.ChannelDecisionDTO;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.SaleChannelDO;
import com.lotflow.entity.enums.DomainEnums.ChannelDecisionStatus;
import com.lotflow.entity.enums.DomainEnums.ListingStatus;
import com.lotflow.entity.enums.DomainEnums.SaleChannelStatus;
import com.lotflow.entity.enums.DomainEnums.SaleChannelType;
import com.lotflow.mapper.AuctionMapper;
import com.lotflow.mapper.DatabaseTimeMapper;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.mapper.SaleChannelMapper;
import com.lotflow.service.ChannelService;
import com.lotflow.service.DepositService;
import com.lotflow.service.OutboxService;
import com.lotflow.vo.SaleChannelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 销售渠道唯一性、流拍处置和自动降级状态机。 */
@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {

    private final SaleChannelMapper saleChannelMapper;
    private final ListingMapper listingMapper;
    private final AuctionMapper auctionMapper;
    private final DepositService depositService;
    private final OutboxService outboxService;
    private final DatabaseTimeMapper timeMapper;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @Override
    public void createAuctionChannel(ListingDO listing, OffsetDateTime start, OffsetDateTime end) {
        createChannel(listing, SaleChannelType.AUCTION, SaleChannelStatus.SCHEDULED,
                listing.getStartPrice(), start, end);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @Override
    public void onAuctionUnsold(ListingDO listing, OffsetDateTime now) {
        if (Boolean.TRUE.equals(listing.getIsAutoDowngrade())) {
            // 授权链只在拍卖确实流拍后启动秒杀，审核通过不能绕过拍卖直接销售。
            startSeckill(listing, now);
            return;
        }
        SaleChannelDO auctionChannel = requireLatestChannelForUpdate(listing.getId());
        openDecision(auctionChannel, listing, now.plusHours(24));
        outboxService.append("channel.decision-required", "listing", listing.getId(),
                Map.of("expiresAt", auctionChannel.getDecisionExpiresAt()));
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @Override
    public void onAuctionBuyerDefaulted(ListingDO listing, OffsetDateTime now) {
        // 中标违约后库存不能立即重卖；先冻结渠道状态，给卖家十二小时决定是否顺延或降级。
        SaleChannelDO auctionChannel = requireLatestChannelForUpdate(listing.getId());
        openDecision(auctionChannel, listing, now.plusHours(12));
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @Override
    public void startShopAfterSeckill(ListingDO listing, OffsetDateTime now) {
        startShop(listing, now);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void decide(long sellerId, long listingId, ChannelDecisionDTO request) {
        ListingDO listing = lockSellerListing(sellerId, listingId);
        SaleChannelDO channel = saleChannelMapper.selectWaitingDecisionForUpdate(listingId);
        OffsetDateTime now = timeMapper.now();
        requireDecisionWindow(channel, now);

        channel.setDecisionValue(request.decision());
        channel.setDecisionStatus(ChannelDecisionStatus.DECIDED);
        saleChannelMapper.updateById(channel);
        // 下一渠道开始前释放上一场拍卖的卖家担保，避免同一库存被重复占用资金。
        releaseLatestSellerDeposit(listingId);

        switch (request.decision()) {
            case SECKILL -> startSeckillFromDecision(listing, request.seckillPrice(), now);
            case RETURN_INVENTORY -> returnInventory(listing);
            case REAUCTION -> prepareReauction(listing, now);
        }
        outboxService.append("channel.seller-decided", "listing", listingId,
                Map.of("decision", request.decision().name()));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void processDueChannel(long channelId) {
        SaleChannelDO snapshot = saleChannelMapper.selectById(channelId);
        if (snapshot == null || snapshot.getStatus() != SaleChannelStatus.ACTIVE) {
            return;
        }
        ListingDO listing = listingMapper.selectByIdForUpdate(snapshot.getListingId());
        SaleChannelDO channel = saleChannelMapper.selectByIdForUpdate(channelId);
        if (channel == null || channel.getStatus() != SaleChannelStatus.ACTIVE) {
            return;
        }
        OffsetDateTime now = timeMapper.now();
        if (channel.getEndsAt().isAfter(now)) {
            return;
        }
        channel.setStatus(SaleChannelStatus.UNSOLD);
        saleChannelMapper.updateById(channel);
        if (channel.getChannel() == SaleChannelType.SECKILL && canEnterShop(listing)) {
            startShop(listing, now);
        } else if (channel.getChannel() != SaleChannelType.AUCTION) {
            returnInventory(listing);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void expireDecision(long channelId) {
        SaleChannelDO snapshot = saleChannelMapper.selectById(channelId);
        if (snapshot == null || snapshot.getDecisionStatus() != ChannelDecisionStatus.WAITING) {
            return;
        }
        ListingDO listing = listingMapper.selectByIdForUpdate(snapshot.getListingId());
        SaleChannelDO channel = saleChannelMapper.selectByIdForUpdate(channelId);
        OffsetDateTime now = timeMapper.now();
        if (channel == null || channel.getDecisionStatus() != ChannelDecisionStatus.WAITING
                || channel.getDecisionExpiresAt().isAfter(now)) {
            return;
        }
        // 决策超时只释放既有担保并回到库存；没有卖家明确授权时禁止系统替其低价出售。
        channel.setDecisionStatus(ChannelDecisionStatus.EXPIRED);
        saleChannelMapper.updateById(channel);
        releaseLatestSellerDeposit(channel.getListingId());
        returnInventory(listing);
        outboxService.append("channel.decision-expired", "listing", listing.getId(),
                Map.of("channelId", channel.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delistShop(long sellerId, long listingId) {
        ListingDO listing = lockSellerListing(sellerId, listingId);
        if (listing.getStatus() != ListingStatus.SHOP) {
            throw new BusinessException(ResultCode.CONFLICT, "只有商城在售商品可以下架");
        }
        SaleChannelDO channel = saleChannelMapper.selectActiveForUpdate(listingId);
        if (channel == null || channel.getChannel() != SaleChannelType.SHOP
                || channel.getStatus() != SaleChannelStatus.ACTIVE) {
            throw new BusinessException(ResultCode.CONFLICT, "商城渠道不在在售状态");
        }
        // 下架只终止在售渠道，已支付订单继续履约；渠道转 CANCELLED 后唯一在售索引释放，商品可重新发布。
        channel.setStatus(SaleChannelStatus.CANCELLED);
        saleChannelMapper.updateById(channel);
        listing.setStatus(ListingStatus.RETURNED);
        listingMapper.updateById(listing);
        outboxService.append("channel.delisted", "listing", listingId,
                Map.of("channelId", channel.getId()));
    }

    @Transactional(readOnly = true)
    @Override
    public List<SaleChannelVO> listChannels(long sellerId, long listingId) {
        ListingDO listing = listingMapper.selectById(listingId);
        if (listing == null || !listing.getSellerId().equals(sellerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该商品渠道");
        }
        return saleChannelMapper.selectList(new LambdaQueryWrapper<SaleChannelDO>()
                        .eq(SaleChannelDO::getListingId, listingId)
                        .orderByDesc(SaleChannelDO::getId))
                .stream().map(SaleChannelVO::from).toList();
    }

    private ListingDO lockSellerListing(long sellerId, long listingId) {
        ListingDO listing = listingMapper.selectByIdForUpdate(listingId);
        if (listing == null || !listing.getSellerId().equals(sellerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权处理该商品");
        }
        return listing;
    }

    private SaleChannelDO requireLatestChannelForUpdate(long listingId) {
        SaleChannelDO channel = saleChannelMapper.selectLatestForUpdate(listingId);
        if (channel == null) {
            throw new BusinessException(ResultCode.CONFLICT, "商品缺少销售渠道事实");
        }
        return channel;
    }

    private void openDecision(SaleChannelDO channel, ListingDO listing, OffsetDateTime expiresAt) {
        channel.setDecisionStatus(ChannelDecisionStatus.WAITING);
        channel.setDecisionValue(null);
        channel.setDecisionExpiresAt(expiresAt);
        saleChannelMapper.updateById(channel);
        listing.setStatus(ListingStatus.WAITING_SELLER_DECISION);
        listingMapper.updateById(listing);
    }

    private void requireDecisionWindow(SaleChannelDO channel, OffsetDateTime now) {
        if (channel == null || channel.getDecisionStatus() != ChannelDecisionStatus.WAITING
                || channel.getDecisionExpiresAt() == null
                || !channel.getDecisionExpiresAt().isAfter(now)) {
            throw new BusinessException(ResultCode.CONFLICT, "卖家决定窗口已结束");
        }
    }

    private void startSeckillFromDecision(ListingDO listing, String requestedPrice,
                                          OffsetDateTime now) {
        BigDecimal seckillPrice = parseDecisionPrice(requestedPrice, listing.getSeckillPrice());
        if (seckillPrice == null || seckillPrice.signum() <= 0
                || seckillPrice.compareTo(listing.getStartPrice()) > 0
                || listing.getShopPrice() != null
                && seckillPrice.compareTo(listing.getShopPrice()) < 0) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT,
                    "秒杀价必须满足商城价 <= 秒杀价 <= 起拍价");
        }
        listing.setSeckillPrice(seckillPrice);
        startSeckill(listing, now);
    }

    private void startSeckill(ListingDO listing, OffsetDateTime now) {
        requirePriceOrder(listing, listing.getSeckillPrice(), "商品未设置有效的秒杀价");
        listing.setStatus(ListingStatus.SECKILL);
        listingMapper.updateById(listing);
        OffsetDateTime endsAt = now.plusHours(24);
        createChannel(listing, SaleChannelType.SECKILL, SaleChannelStatus.ACTIVE,
                listing.getSeckillPrice(), now, endsAt);
        outboxService.append("channel.seckill-started", "listing", listing.getId(),
                Map.of("endsAt", endsAt));
    }

    private void startShop(ListingDO listing, OffsetDateTime now) {
        requirePriceOrder(listing, listing.getShopPrice(), "商品未获得商城降级授权");
        listing.setStatus(ListingStatus.SHOP);
        listingMapper.updateById(listing);
        OffsetDateTime endsAt = now.plusDays(30);
        createChannel(listing, SaleChannelType.SHOP, SaleChannelStatus.ACTIVE,
                listing.getShopPrice(), now, endsAt);
        outboxService.append("channel.shop-started", "listing", listing.getId(),
                Map.of("endsAt", endsAt));
    }

    private void prepareReauction(ListingDO listing, OffsetDateTime now) {
        listing.setStatus(ListingStatus.APPROVED);
        listing.setScheduledStart(now.plusMinutes(1));
        listingMapper.updateById(listing);
    }

    private void returnInventory(ListingDO listing) {
        listing.setStatus(ListingStatus.RETURNED);
        listingMapper.updateById(listing);
        outboxService.append("channel.returned-to-inventory", "listing", listing.getId(),
                Map.of("listingId", listing.getId()));
    }

    private boolean canEnterShop(ListingDO listing) {
        return Boolean.TRUE.equals(listing.getIsAutoDowngrade())
                && hasValidPriceOrder(listing, listing.getShopPrice());
    }

    private void requirePriceOrder(ListingDO listing, BigDecimal price, String message) {
        if (!hasValidPriceOrder(listing, price)) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, message);
        }
    }

    private boolean hasValidPriceOrder(ListingDO listing, BigDecimal price) {
        return price != null && price.signum() > 0
                && (listing.getSeckillPrice() == null || price.compareTo(listing.getSeckillPrice()) <= 0)
                && price.compareTo(listing.getStartPrice()) <= 0;
    }

    private void createChannel(ListingDO listing, SaleChannelType type, SaleChannelStatus status,
                               BigDecimal price, OffsetDateTime start, OffsetDateTime end) {
        SaleChannelDO channel = new SaleChannelDO();
        channel.setListingId(listing.getId());
        channel.setChannel(type);
        channel.setStatus(status);
        channel.setPrice(price);
        channel.setStartsAt(start);
        channel.setEndsAt(end);
        channel.setDecisionStatus(null);
        saleChannelMapper.insert(channel);
    }

    private BigDecimal parseDecisionPrice(String requested, BigDecimal fallback) {
        String value = requested == null || requested.isBlank()
                ? (fallback == null ? null : fallback.toPlainString()) : requested;
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "秒杀价最多两位小数");
        }
    }

    private void releaseLatestSellerDeposit(long listingId) {
        Long auctionId = auctionMapper.selectLatestIdByListing(listingId);
        if (auctionId != null) {
            depositService.releaseSellerDeposit(auctionId);
        }
    }
}
