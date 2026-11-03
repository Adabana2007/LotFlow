package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.lotflow.entity.enums.DomainEnums.DisputeResolutionValue;
import jakarta.validation.constraints.NotNull;

/** 人工争议处理结果。 */
public record DisputeResolutionDTO(
        @NotNull DisputeResolutionValue outcome,
        @NotBlank @Size(max = 2000) String resolution
) {
}
