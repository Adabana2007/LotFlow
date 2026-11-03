package com.lotflow.vo;

import com.lotflow.entity.AiTaskDO;

/** AI 任务状态、Token 用量和追踪摘要。 */
public record AiTaskVO(String id, String taskType, String status, String outputSummary,
                       String modelName, String promptVersion, Integer promptTokens,
                       Integer completionTokens, Long latencyMs, String traceId, String errorCode,
                       java.time.OffsetDateTime createdAt) {
    public static AiTaskVO from(AiTaskDO task) {
        return new AiTaskVO(task.getId().toString(), task.getTaskType(), task.getStatus().name(),
                task.getOutputSummary(), task.getModelName(), task.getPromptVersion(),
                task.getPromptTokens(), task.getCompletionTokens(), task.getLatencyMs(),
                task.getTraceId(), task.getErrorCode(), task.getGmtCreate());
    }
}
