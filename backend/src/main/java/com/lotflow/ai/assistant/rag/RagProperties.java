package com.lotflow.ai.assistant.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 平台规则知识库、检索和索引协调配置。 */
@ConfigurationProperties("lotflow.ai.rag")
public record RagProperties(
        boolean enabled,
        String collectionName,
        int topK,
        double similarityThreshold,
        int chunkSize,
        int minChunkSizeChars,
        int minChunkLengthToEmbed,
        int maxChunksPerFile,
        Duration indexLockTtl,
        Duration readyTtl
) {
}
