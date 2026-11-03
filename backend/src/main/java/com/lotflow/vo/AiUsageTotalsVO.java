package com.lotflow.vo;

/** 最近窗口内的 AI 调用总量，仅统计已持久化审计记录。 */
public record AiUsageTotalsVO(
        long totalCalls,
        long succeededCalls,
        long failedCalls,
        long promptTokens,
        long completionTokens,
        Long averageLatencyMs
) {
}
