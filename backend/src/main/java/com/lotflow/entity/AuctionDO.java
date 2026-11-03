package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lotflow.entity.enums.DomainEnums.AuctionStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 拍卖场次。 */
@Getter
@Setter
@TableName("auction")
public class AuctionDO extends BaseDO {
    private Long listingId;
    private Long sellerId;
    private Integer attemptNo;
    private BigDecimal startPrice;
    private BigDecimal currentPrice;
    private AuctionStatus status;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Integer extensionSeconds;
    private Long highestBidderId;
    private Long highestBidId;
}
