package com.lotflow.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 争议复用订单事实时的证据、资金与人工处理摘要。 */
@Getter
@Setter
public class DisputeVO {
    private String id;
    private String orderId;
    private String orderNo;
    private String listingId;
    private String listingTitle;
    private String buyerId;
    private String buyerName;
    private String sellerId;
    private String sellerName;
    private String amount;
    private String depositApplied;
    private String escrowAmount;
    private String orderStatus;
    private String disputeStatus;
    private String reason;
    private String evidence;
    private String resolution;
    private String resolvedBy;
    private OffsetDateTime paidAt;
    private OffsetDateTime shippedAt;
    private OffsetDateTime receivedAt;
    private OffsetDateTime disputeOpenedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime resolvedAt;
}
