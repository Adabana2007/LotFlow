package com.lotflow.ai.assistant;

import com.lotflow.ai.AiProperties;
import com.lotflow.ai.assistant.rag.KnowledgeIndexService;
import com.lotflow.ai.tools.AssistantTools;
import com.lotflow.common.BusinessException;
import com.lotflow.service.AssistantConversationService;
import com.lotflow.service.ListingService;
import com.lotflow.service.OrderService;
import com.lotflow.service.WalletService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 平台规则问答和用户数据只读查询。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final int MAX_ERROR_LENGTH = 500;
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[K(\\d+)]");

    @Qualifier("assistantChatClient")
    private final ObjectProvider<ChatClient> chatClientProvider;
    @Qualifier("assistantChatMemory")
    private final ChatMemory chatMemory;
    private final AssistantConversationService conversationService;
    private final AiProperties properties;
    private final KnowledgeIndexService knowledgeIndexService;
    private final ListingService listingService;
    private final OrderService orderService;
    private final WalletService walletService;

    /**
     * 流式回答：逐 token 推送正文，结束时推送本次实际引用的来源。
     * 响应头发出后无法再改状态码，模型或知识库故障统一以 error 事件结束。
     */
    public Flux<AssistantEvent> stream(long userId, long conversationId, String question) {
        conversationService.requireOwned(userId, conversationId);
        conversationService.renameIfDefault(userId, conversationId, question);
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (!properties.chatConfigured() || chatClient == null) {
            // 平台助手依赖对话模型；未配置 Key 时明确返回不可用，不能伪造规则答案。
            return Flux.just(AssistantEvent.error("平台助手暂不可用"));
        }
        try {
            knowledgeIndexService.synchronizeActiveVersion();
        } catch (BusinessException exception) {
            return Flux.just(AssistantEvent.error(exception.getMessage()));
        }
        if (!knowledgeIndexService.status().ready()) {
            return Flux.just(AssistantEvent.error("平台规则知识库暂不可用"));
        }
        // 工具实例绑定当前登录用户，模型只能调用查询方法，不能通过参数切换成其他用户。
        AssistantTools tools = new AssistantTools(userId, listingService, orderService, walletService);
        StringBuilder answer = new StringBuilder();
        AtomicReference<Map<String, Object>> advisorContext = new AtomicReference<>(Map.of());
        // 单个 SSE 订阅内 token 串行到达，累加正文不需要额外同步。
        return chatClient.prompt()
                .system(new ClassPathResource("prompts/v1/assistant.st"))
                .user(question)
                .tools(tools)
                // 记忆按会话隔离：会话 ID 由服务端生成，模型和客户端都无法指定别人的会话。
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID,
                        AssistantConversationService.memoryId(conversationId)))
                .stream()
                .chatClientResponse()
                .map(response -> {
                    if (!response.context().isEmpty()) {
                        advisorContext.set(response.context());
                    }
                    String delta = delta(response.chatResponse());
                    answer.append(delta);
                    return AssistantEvent.token(delta);
                })
                .filter(event -> !event.text().isEmpty())
                .concatWith(Mono.fromSupplier(
                        () -> AssistantEvent.done(citations(answer.toString(), advisorContext.get()))))
                .onErrorResume(BusinessException.class,
                        exception -> Flux.just(AssistantEvent.error(exception.getMessage())))
                .onErrorResume(RuntimeException.class, exception -> {
                    log.warn("平台助手流式回答失败: {}", diagnostic(exception));
                    return Flux.just(AssistantEvent.error("平台助手暂不可用"));
                })
                // 记忆在流结束前已写入，touch 只更新会话列表的活跃时间，失败不影响已经返回的回答。
                .doOnComplete(() -> touchQuietly(conversationId));
    }

    private void touchQuietly(long conversationId) {
        try {
            conversationService.touch(conversationId);
        } catch (RuntimeException exception) {
            log.warn("助手会话活跃时间更新失败, conversationId={}", conversationId, exception);
        }
    }

    private String delta(ChatResponse response) {
        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private List<String> citations(String answer, Map<String, Object> context) {
        Object documents = context.get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        if (!(documents instanceof List<?> values)) {
            return List.of();
        }
        List<Document> documentsList = values.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .toList();
        Set<Integer> referencedNumbers = referencedCitationNumbers(answer);
        List<String> citations = new ArrayList<>(referencedNumbers.size());
        for (int citationNumber : referencedNumbers) {
            if (citationNumber < 1 || citationNumber > documentsList.size()) {
                continue;
            }
            Document document = documentsList.get(citationNumber - 1);
            citations.add("[K" + citationNumber + "] "
                    + metadata(document, "title") + " · " + metadata(document, "sourcePath"));
        }
        return List.copyOf(citations);
    }

    private Set<Integer> referencedCitationNumbers(String answer) {
        // Top-K 只是检索候选；只回传回答正文明确使用的来源，避免未采用的片段被展示成引用。
        Set<Integer> citationNumbers = new LinkedHashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            String number = matcher.group(1);
            if (number.length() <= 3) {
                citationNumbers.add(Integer.parseInt(number));
            }
        }
        return citationNumbers;
    }

    private String metadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? "平台规则" : value.toString();
    }

    private String diagnostic(RuntimeException exception) {
        String message = exception.getMessage();
        String error = message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    public record AssistantEvent(String type, String text, List<String> citations) {

        private static AssistantEvent token(String text) {
            return new AssistantEvent("token", text, List.of());
        }

        private static AssistantEvent done(List<String> citations) {
            return new AssistantEvent("done", "", citations);
        }

        private static AssistantEvent error(String message) {
            return new AssistantEvent("error", message, List.of());
        }
    }
}
