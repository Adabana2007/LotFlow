package com.lotflow.ai.review;

import com.lotflow.entity.enums.DomainEnums.RiskLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** AI 审核风险标签，不作为最终审核结论。 */
public record ReviewRiskAiResult(
        @NotNull RiskLevel riskLevel,
        List<String> matchedRules,
        @Size(max = 3000) String evidenceSummary
) {
}
