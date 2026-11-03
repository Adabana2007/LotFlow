package com.lotflow.messaging;

/** AI 审核和聊天风险事件的最小载荷。 */
public record AiTaskEvent(Long aiTaskId, Long listingId, Long reviewGeneration, Long messageId) {
}
