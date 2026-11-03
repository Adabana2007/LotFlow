package com.lotflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 人工信用调整；单次变化受平台信用分总区间约束。 */
public record CreditAdjustmentDTO(@Min(-120) @Max(120) int changeValue,
                                  @NotBlank @Size(max = 500) String reason) {
}
