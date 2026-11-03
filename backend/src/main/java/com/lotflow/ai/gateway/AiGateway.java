package com.lotflow.ai.gateway;

import com.lotflow.ai.AiProperties;
import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** 统一的模型调用、结构化输出和调用元数据。 */
@Component
@RequiredArgsConstructor
public class AiGateway {

    @Qualifier("deepSeekChatClient")
    private final ObjectProvider<ChatClient> chatClientProvider;
    private final AiProperties properties;

    public <T> AiCallResult<T> structured(String promptResource, String userText, Class<T> type) {
        requireConfigured();
        RuntimeException last = null;
        // 结构化输出最多重试一次；仍失败时由调用方转人工或降级。
        for (int attempt = 0; attempt < 2; attempt++) {
            long started = System.nanoTime();
            try {
                var response = chatClient().prompt()
                        .system(new ClassPathResource(promptResource))
                        .user(userText)
                        .call()
                        .responseEntity(type);
                return result(response.entity(), response.response(), started);
            } catch (RuntimeException exception) {
                last = exception;
            }
        }
        throw new BusinessException(ResultCode.AI_UNAVAILABLE, "AI 结构化输出失败", last);
    }

    public AiCallResult<String> text(String promptResource, String userText) {
        requireConfigured();
        long started = System.nanoTime();
        try {
            ChatResponse response = chatClient().prompt()
                    .system(new ClassPathResource(promptResource))
                    .user(userText)
                    .call()
                    .chatResponse();
            String content = response.getResult().getOutput().getText();
            return result(content, response, started);
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.AI_UNAVAILABLE, "AI 服务暂不可用", exception);
        }
    }

    public <T> AiCallResult<T> structuredWithMedia(String promptResource, String userText,
                                                   List<Media> media, Class<T> type) {
        requireConfigured();
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            long started = System.nanoTime();
            try {
                var response = chatClient().prompt()
                        .system(new ClassPathResource(promptResource))
                        .user(spec -> {
                            spec.text(userText);
                            if (!media.isEmpty()) {
                                spec.media(media.toArray(Media[]::new));
                            }
                        })
                        .call()
                        .responseEntity(type);
                return result(response.entity(), response.response(), started);
            } catch (RuntimeException exception) {
                last = exception;
            }
        }
        throw new BusinessException(ResultCode.AI_UNAVAILABLE, "AI 结构化输出失败", last);
    }

    private <T> AiCallResult<T> result(T value, ChatResponse response, long started) {
        // 模型名、Token、延迟和 traceId 随每次结果保存，便于审计和成本分析。
        Usage usage = response.getMetadata().getUsage();
        String model = response.getMetadata().getModel();
        long latency = (System.nanoTime() - started) / 1_000_000;
        return new AiCallResult<>(value, model, usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(), latency, MDC.get("traceId"));
    }

    private void requireConfigured() {
        if (!properties.chatConfigured()) {
            throw new BusinessException(ResultCode.AI_UNAVAILABLE, "AI 服务未配置");
        }
    }

    private ChatClient chatClient() {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            throw new BusinessException(ResultCode.AI_UNAVAILABLE, "AI 模型未启用");
        }
        return chatClient;
    }

    public record AiCallResult<T>(T value, String model, Integer promptTokens,
                                  Integer completionTokens, long latencyMs, String traceId) {
    }
}
