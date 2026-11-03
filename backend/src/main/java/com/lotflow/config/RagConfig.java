package com.lotflow.config;

import com.lotflow.ai.assistant.rag.KnowledgeIndexState;
import com.lotflow.ai.assistant.rag.RagDocumentFormatter;
import com.lotflow.ai.assistant.rag.RagProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

/** 平台助手的官方 RAG Advisor 和独立 ChatClient。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "milvus")
@ConditionalOnProperty(name = "lotflow.ai.rag.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("'${lotflow.ai.dashscope-key:}' != ''")
public class RagConfig {

    private static final String PENDING_CORPUS_VERSION = "__PENDING__";
    private static final String PUBLIC_AUDIENCE = "PUBLIC";

    @Bean("assistantChatClient")
    @Lazy
    public ChatClient assistantChatClient(
            @Qualifier("deepSeekChatModel") OpenAiChatModel chatModel,
            @Qualifier("policyVectorStore") VectorStore policyVectorStore,
            RagProperties properties,
            KnowledgeIndexState indexState,
            @Qualifier("aiTaskExecutor") TaskExecutor aiTaskExecutor) {
        PromptTemplate contextPrompt = new PromptTemplate(
                new ClassPathResource("prompts/v1/rag-context.st"));
        PromptTemplate emptyContextPrompt = new PromptTemplate(
                new ClassPathResource("prompts/v1/rag-empty-context.st"));
        ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .promptTemplate(contextPrompt)
                .emptyContextPromptTemplate(emptyContextPrompt)
                // 用户询问自己的订单或钱包时可能没有规则命中，仍应允许只读工具完成回答。
                .allowEmptyContext(true)
                .documentFormatter(RagDocumentFormatter::format)
                .build();
        VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(policyVectorStore)
                .topK(properties.topK())
                .similarityThreshold(properties.similarityThreshold())
                .filterExpression(() -> activeCorpusFilter(indexState))
                .build();
        RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(queryAugmenter)
                .taskExecutor(aiTaskExecutor)
                .build();
        // 助手 ChatClient 只用于带 RAG 的规则问答，其他结构化 AI 任务继续使用基础 ChatClient。
        return ChatClient.builder(chatModel).defaultAdvisors(advisor).build();
    }

    private org.springframework.ai.vectorstore.filter.Filter.Expression activeCorpusFilter(
            KnowledgeIndexState indexState) {
        String corpusVersion = indexState.activeVersion();
        String version = corpusVersion == null ? PENDING_CORPUS_VERSION : corpusVersion;
        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        // 只检索当前已成功发布的语料版本，避免重建期间混合新旧规则或检索到其他受众的片段。
        return filters.and(filters.eq("corpusVersion", version),
                filters.eq("audience", PUBLIC_AUDIENCE)).build();
    }
}
