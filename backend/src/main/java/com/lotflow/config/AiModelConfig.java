package com.lotflow.config;

import com.lotflow.ai.AiProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/** DeepSeek 对话模型和百炼向量模型配置。 */
@Configuration
@RequiredArgsConstructor
public class AiModelConfig {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_RETRIES = 1;
    private static final int EMBEDDING_DIMENSIONS = 1024;

    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;

    @Bean("deepSeekChatModel")
    @ConditionalOnExpression("'${lotflow.ai.deepseek-key:}' != ''")
    public OpenAiChatModel deepSeekChatModel(AiProperties properties) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(properties.deepseekBaseUrl())
                .apiKey(properties.deepseekKey())
                .model(properties.deepseekModel())
                .temperature(0.2)
                .timeout(REQUEST_TIMEOUT)
                .maxRetries(MAX_RETRIES)
                .build();
        // 两个供应商的地址和密钥互不相同，交由 Spring AI Builder 建立各自独立的底层客户端与观测链路。
        return OpenAiChatModel.builder()
                .options(options)
                .observationRegistry(observationRegistry)
                .meterRegistry(meterRegistry)
                .httpClientBuilderCustomizer(builder -> builder.meterTags(List.of(Tag.of("ai.provider", "deepseek"))))
                .build();
    }

    @Bean("deepSeekChatClient")
    @ConditionalOnExpression("'${lotflow.ai.deepseek-key:}' != ''")
    public ChatClient deepSeekChatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("dashScopeEmbeddingModel")
    @ConditionalOnExpression("'${lotflow.ai.dashscope-key:}' != ''")
    public EmbeddingModel dashScopeEmbeddingModel(AiProperties properties) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .baseUrl(properties.dashscopeBaseUrl())
                .apiKey(properties.dashscopeKey())
                .model(properties.embeddingModel())
                .dimensions(EMBEDDING_DIMENSIONS)
                .timeout(REQUEST_TIMEOUT)
                .maxRetries(MAX_RETRIES)
                .build();
        // 向量维度必须与 Milvus Collection 的 1024 维约束一致，避免写入时出现维度不匹配。
        return OpenAiEmbeddingModel.builder()
                .options(options)
                .observationRegistry(observationRegistry)
                .httpClientBuilderCustomizer(builder -> builder.meterTags(List.of(Tag.of("ai.provider", "dashscope"))))
                .build();
    }
}
