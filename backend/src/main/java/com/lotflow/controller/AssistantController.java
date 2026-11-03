package com.lotflow.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotflow.ai.assistant.AssistantService;
import com.lotflow.common.CursorPageVO;
import com.lotflow.dto.AssistantQueryDTO;
import com.lotflow.dto.CreateAssistantConversationDTO;
import com.lotflow.security.CurrentUser;
import com.lotflow.service.AssistantConversationService;
import com.lotflow.vo.AssistantConversationVO;
import com.lotflow.vo.AssistantMessageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.List;

/** 平台助手的会话列表、历史回放和流式问答。 */
@RestController
@RequestMapping("/api/v1/ai/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;
    private final AssistantConversationService conversationService;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    @GetMapping("/conversations")
    public CursorPageVO<AssistantConversationVO> list(@RequestParam(required = false) String cursor,
                                                      @RequestParam(defaultValue = "20") int limit) {
        return conversationService.list(currentUser.id(), cursor, limit);
    }

    @PostMapping("/conversations")
    public ResponseEntity<AssistantConversationVO> create(
            @Valid @RequestBody CreateAssistantConversationDTO request) {
        AssistantConversationVO conversation = conversationService.create(currentUser.id(), request.title());
        return ResponseEntity.created(URI.create(
                "/api/v1/ai/assistant/conversations/" + conversation.id())).body(conversation);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<AssistantMessageVO> messages(@PathVariable long conversationId) {
        // 只有会话所有者可以回放历史，越权统一按不存在处理。
        return conversationService.messages(currentUser.id(), conversationId);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> delete(@PathVariable long conversationId) {
        conversationService.delete(currentUser.id(), conversationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/conversations/{conversationId}/queries",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> query(@PathVariable long conversationId,
                                               @Valid @RequestBody AssistantQueryDTO request) {
        // data 先序列化成 JSON 字符串，SSE 层不依赖事件对象的媒体类型转换。
        return assistantService.stream(currentUser.id(), conversationId, request.question())
                .map(event -> ServerSentEvent.<String>builder()
                        .event(event.type())
                        .data(serialize(event))
                        .build());
    }

    private String serialize(AssistantService.AssistantEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize assistant event", exception);
        }
    }
}
