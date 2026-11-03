package com.lotflow.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 个人订单列表中的交易摘要和当前账号角色。 */
@Getter
@Setter
public class OrderSummaryVO {
    private String id;
    private String orderNo;
    private String listingId;
    private String listingTitle;
    private String buyerId;
    private String sellerId;
    private String counterpartyName;
    private String role;
    private String sourceType;
    private String amount;
    private String status;
    private Boolean offeredSecondChance;
    private OffsetDateTime paymentDeadline;
    private OffsetDateTime shipDeadline;
    private OffsetDateTime createdAt;
    private String disputeStatus;
    private String reservationStatus;
    private OffsetDateTime reservationExpiresAt;
}
