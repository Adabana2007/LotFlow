package com.lotflow.vo;

import com.lotflow.entity.AuctionDO;

import java.time.OffsetDateTime;

/** 拍卖当前状态。 */
public record AuctionVO(
        String id,
        String listingId,
        String sellerId,
        String startPrice,
        String currentPrice,
        String status,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        int extensionSeconds,
        String highestBidderId
) {
    public static AuctionVO from(AuctionDO auction) {
        return new AuctionVO(auction.getId().toString(), auction.getListingId().toString(),
                auction.getSellerId().toString(), auction.getStartPrice().toPlainString(),
                auction.getCurrentPrice().toPlainString(), auction.getStatus().name(),
                auction.getStartTime(), auction.getEndTime(), auction.getExtensionSeconds(),
                auction.getHighestBidderId() == null ? null : auction.getHighestBidderId().toString());
    }
}
