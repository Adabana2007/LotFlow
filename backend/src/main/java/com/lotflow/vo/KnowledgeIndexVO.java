package com.lotflow.vo;

/** 平台规则知识库索引状态。 */
public record KnowledgeIndexVO(boolean ready, String activeVersion, String lastError) {
}
