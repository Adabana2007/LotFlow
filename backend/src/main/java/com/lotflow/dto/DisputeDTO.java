package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 争议发起请求。 */
public record DisputeDTO(
        @NotBlank @Size(max = 2000) String reason,
        @Size(max = 3000) String evidenceSummary
) {
}
