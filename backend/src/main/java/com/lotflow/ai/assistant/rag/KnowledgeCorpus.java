package com.lotflow.ai.assistant.rag;

import org.springframework.ai.document.Document;

import java.util.List;

/** 一次知识库构建使用的版本和规则片段。 */
public record KnowledgeCorpus(String version, List<Document> documents) {
}
