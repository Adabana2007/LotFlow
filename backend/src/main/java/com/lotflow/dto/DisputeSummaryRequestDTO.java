package com.lotflow.dto;

import jakarta.validation.constraints.NotNull;

/** 争议摘要任务对象。 */
public record DisputeSummaryRequestDTO(@NotNull Long orderId) {
}
