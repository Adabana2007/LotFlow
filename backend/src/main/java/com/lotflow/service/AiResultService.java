package com.lotflow.service;

import com.lotflow.ai.chat.ChatRiskAiResult;
import com.lotflow.ai.gateway.AiGateway;

/** AI 调用结果持久化。 */
public interface AiResultService {

    void markRunning(long taskId);

    void completeListingDraft(long taskId, Object output, AiGateway.AiCallResult<?> call,
                              String promptVersion);

    void completeReview(long taskId, long listingId, long reviewGeneration, Object output,
                        AiGateway.AiCallResult<?> call, String promptVersion,
                        boolean policyAutoApprovalEligible);

    void completeChatRisk(long taskId, long messageId, ChatRiskAiResult output,
                          AiGateway.AiCallResult<?> call, String promptVersion);

    void completeGeneric(long taskId, Object output, AiGateway.AiCallResult<?> call,
                         String promptVersion);

    void fail(long taskId, String errorCode);
}
