package com.lotflow.vo;

import com.lotflow.entity.OrderDO;

import java.time.OffsetDateTime;

/** 订单详情。 */
public record OrderVO(
        String id,
        String orderNo,
        String listingId,
        String buyerId,
        String sellerId,
        String sourceType,
        String amount,
        String depositApplied,
        String status,
        OffsetDateTime paymentDeadline,
        OffsetDateTime shipDeadline,
        OffsetDateTime shippedAt,
        OffsetDateTime receiptDeadline,
        OffsetDateTime receivedAt,
        OffsetDateTime disputeDeadline,
        OffsetDateTime disputeOpenedAt,
        Boolean offeredSecondChance,
        String escrowAmount,
        OffsetDateTime paidAt,
        String carrier,
        String trackingNo,
        String disputeStatus,
        String disputeReason,
        String disputeEvidence,
        String disputeResolution,
        OffsetDateTime disputeResolvedAt,
        String reservationStatus,
        OffsetDateTime reservationExpiresAt
) {
    public static OrderVO from(OrderDO order) {
        return new OrderVO(order.getId().toString(), order.getOrderNo(),
                order.getListingId().toString(), order.getBuyerId().toString(),
                order.getSellerId().toString(), order.getSourceType(),
                order.getAmount().toPlainString(), order.getDepositApplied().toPlainString(),
                order.getStatus().name(), order.getPaymentDeadline(), order.getShipDeadline(),
                order.getShippedAt(), order.getReceiptDeadline(), order.getReceivedAt(),
                order.getDisputeDeadline(), order.getDisputeOpenedAt(),
                order.getOfferedSecondChance(),
                order.getEscrowAmount() == null ? null : order.getEscrowAmount().toPlainString(),
                order.getPaidAt(), order.getCarrier(), order.getTrackingNo(),
                order.getDisputeStatus() == null ? null : order.getDisputeStatus().name(),
                order.getDisputeReason(), order.getDisputeEvidence(),
                order.getDisputeResolution(), order.getDisputeResolvedAt(),
                order.getReservationStatus() == null ? null : order.getReservationStatus().name(),
                order.getReservationExpiresAt());
    }
}
