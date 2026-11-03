package com.lotflow.ai.chat;

import com.lotflow.entity.enums.DomainEnums.RiskLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 聊天风险建议，不能触发自动处罚。 */
public record ChatRiskAiResult(
        @NotNull RiskLevel riskLevel,
        List<String> categories,
        @Size(max = 2000) String evidenceSummary
) {
}
