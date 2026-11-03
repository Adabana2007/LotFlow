package com.lotflow.service;

import com.lotflow.dto.ReviewDecisionDTO;
import com.lotflow.entity.enums.DomainEnums.RiskLevel;

/** 商品人工终审和受平台策略约束的 AI 辅助审核。 */
public interface ReviewService {

    void decide(long reviewerId, long listingId, ReviewDecisionDTO request);

    void applyAiRisk(long listingId, long reviewGeneration, RiskLevel riskLevel,
                     String evidence, boolean policyAutoApprovalEligible);
}
