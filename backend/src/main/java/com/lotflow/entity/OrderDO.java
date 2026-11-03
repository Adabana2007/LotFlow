package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lotflow.entity.enums.DomainEnums.DisputeStatus;
import com.lotflow.entity.enums.DomainEnums.OrderStatus;
import com.lotflow.entity.enums.DomainEnums.ReservationStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 订单、支付事实、履约、争议和秒杀预占的统一事实。 */
@Getter
@Setter
@TableName("order_record")
public class OrderDO extends BaseDO {
    private String orderNo;
    private Long listingId;
    private Long auctionId;
    private Long sellerId;
    private Long buyerId;
    private String sourceType;
    private BigDecimal amount;
    private BigDecimal depositApplied;
    private OrderStatus status;
    private OffsetDateTime paymentDeadline;
    private OffsetDateTime shipDeadline;
    private OffsetDateTime shippedAt;
    private OffsetDateTime receiptDeadline;
    private OffsetDateTime receivedAt;
    private OffsetDateTime disputeDeadline;
    private OffsetDateTime disputeOpenedAt;
    private BigDecimal escrowAmount;
    private Boolean offeredSecondChance;
    private String paymentKey;
    private OffsetDateTime paidAt;
    private String carrier;
    private String trackingNo;
    private DisputeStatus disputeStatus;
    private String disputeReason;
    private String disputeEvidence;
    private String disputeResolution;
    private Long disputeResolvedBy;
    private OffsetDateTime disputeResolvedAt;
    private ReservationStatus reservationStatus;
    private OffsetDateTime reservationExpiresAt;
    private String reservationKey;
}
