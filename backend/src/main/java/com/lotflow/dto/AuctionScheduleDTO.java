package com.lotflow.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/** 卖家激活拍卖时可调整一次开始时间。 */
public record AuctionScheduleDTO(@NotNull OffsetDateTime scheduledStart) {
}
