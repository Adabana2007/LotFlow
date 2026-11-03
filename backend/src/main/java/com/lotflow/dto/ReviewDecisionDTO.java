package com.lotflow.dto;

import com.lotflow.entity.enums.DomainEnums.ReviewDecisionValue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 人工审核决定。 */
public record ReviewDecisionDTO(
        @NotNull ReviewDecisionValue decision,
        @NotBlank @Size(max = 2000) String reason
) {
}
