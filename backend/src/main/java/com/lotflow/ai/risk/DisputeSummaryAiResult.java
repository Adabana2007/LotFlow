package com.lotflow.ai.risk;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** 争议时间线摘要和证据缺口。 */
public record DisputeSummaryAiResult(
        @NotBlank String timeline,
        List<String> evidenceGaps,
        @NotBlank String recommendation
) {
}
