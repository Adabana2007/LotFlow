package com.lotflow.vo;

import com.lotflow.entity.MessageDO;

import java.time.OffsetDateTime;

/** 运营复核风险事件。 */
public record RiskEventVO(String id, String messageId, String riskLevel, String categories,
                          String evidenceSummary, boolean requiresHumanReview,
                          OffsetDateTime occurredAt) {
    public static RiskEventVO from(MessageDO result) {
        return new RiskEventVO(result.getId().toString(), result.getId().toString(),
                result.getRiskLevel().name(), result.getRiskCategories(), result.getRiskEvidence(),
                Boolean.TRUE.equals(result.getRequiresHumanReview()), result.getGmtCreate());
    }
}
