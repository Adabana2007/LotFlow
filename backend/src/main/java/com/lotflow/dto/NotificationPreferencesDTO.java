package com.lotflow.dto;

/** 用户明确选择的三类业务通知开关。 */
public record NotificationPreferencesDTO(
        boolean auctionEnding,
        boolean orderPayment,
        boolean messages
) {
}
