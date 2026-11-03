package com.lotflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** 创建拍卖商品草稿。 */
public record CreateListingDTO(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 5000) String description,
        @NotBlank @Size(max = 64) String categoryCode,
        @NotBlank @Size(max = 32) String conditionCode,
        @Size(max = 2000) String defects,
        @NotBlank String startPrice,
        @Min(1) @Max(72) int durationHours,
        @NotNull OffsetDateTime scheduledStart,
        boolean autoDowngrade,
        String seckillPrice,
        String shopPrice,
        Boolean chatEnabled
) {
}
