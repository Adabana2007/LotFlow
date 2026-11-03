package com.lotflow.service;

import com.lotflow.dto.ChannelDecisionDTO;
import com.lotflow.entity.ListingDO;
import com.lotflow.vo.SaleChannelVO;

import java.time.OffsetDateTime;
import java.util.List;

/** 销售渠道状态和流拍降级。 */
public interface ChannelService {

    void createAuctionChannel(ListingDO listing, OffsetDateTime start, OffsetDateTime end);

    void onAuctionUnsold(ListingDO listing, OffsetDateTime now);

    void onAuctionBuyerDefaulted(ListingDO listing, OffsetDateTime now);

    void startShopAfterSeckill(ListingDO listing, OffsetDateTime now);

    void decide(long sellerId, long listingId, ChannelDecisionDTO request);

    void processDueChannel(long channelId);

    void expireDecision(long decisionId);

    /** 卖家主动下架商城在售商品；已支付订单不受影响。 */
    void delistShop(long sellerId, long listingId);

    List<SaleChannelVO> listChannels(long sellerId, long listingId);
}
