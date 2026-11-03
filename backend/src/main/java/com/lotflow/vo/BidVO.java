package com.lotflow.vo;

import com.lotflow.entity.BidDO;
import com.lotflow.entity.AuctionDO;

import java.time.OffsetDateTime;

/** 出价结果。 */
public record BidVO(String id, String auctionId, String bidderId, String amount,
                    String depositRequired, OffsetDateTime createdAt, AuctionVO auction) {

    public static BidVO from(BidDO bid, AuctionDO auction) {
        return new BidVO(bid.getId().toString(), bid.getAuctionId().toString(),
                bid.getBidderId().toString(), bid.getAmount().toPlainString(),
                bid.getDepositRequired().toPlainString(), bid.getGmtCreate(),
                AuctionVO.from(auction));
    }
}
