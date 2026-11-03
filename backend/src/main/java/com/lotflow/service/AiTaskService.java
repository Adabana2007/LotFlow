package com.lotflow.service;

import com.lotflow.dto.ListingDraftRequestDTO;
import com.lotflow.dto.SellerAnalysisRequestDTO;
import com.lotflow.vo.AiTaskVO;

/** AI 任务创建和异步投递。 */
public interface AiTaskService {

    long submitListingDraft(long userId, ListingDraftRequestDTO request);

    long createReviewTask(long listingId, long reviewGeneration);

    long createChatRiskTask(long messageId);

    long submitSellerAnalysis(long userId, SellerAnalysisRequestDTO request);

    long submitDisputeSummary(long userId, long orderId);

    AiTaskVO get(long userId, boolean privileged, long taskId);
}
