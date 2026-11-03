package com.lotflow.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 当前用户完整出价历史中的一条不可变出价。 */
@Getter
@Setter
public class BidHistoryVO {
    private String id;
    private String auctionId;
    private String listingId;
    private String listingTitle;
    private String amount;
    private String depositRequired;
    private String bidStatus;
    private String auctionStatus;
    private String currentPrice;
    private String highestBidderId;
    private OffsetDateTime endsAt;
    private OffsetDateTime createdAt;
}
