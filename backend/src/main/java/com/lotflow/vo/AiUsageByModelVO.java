package com.lotflow.vo;

/** 按模型聚合的 Token 用量与可配置估算成本。 */
public record AiUsageByModelVO(
        String modelName,
        long calls,
        long promptTokens,
        long completionTokens,
        Long averageLatencyMs,
        String estimatedCost
) {
}
