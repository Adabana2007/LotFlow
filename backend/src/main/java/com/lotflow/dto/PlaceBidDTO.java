package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;

/** 出价金额使用字符串传输。 */
public record PlaceBidDTO(@NotBlank String amount) {
}
