package com.lotflow.controller;

import com.lotflow.common.CursorPageVO;
import com.lotflow.dto.ChatMessageDTO;
import com.lotflow.dto.ConversationDTO;
import com.lotflow.security.CurrentUser;
import com.lotflow.service.ChatService;
import com.lotflow.service.PersonalWorkspaceService;
import com.lotflow.vo.ConversationSummaryVO;
import com.lotflow.vo.ConversationVO;
import com.lotflow.vo.MessageVO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/** 会话与消息 REST 接口。 */
@RestController
@RequestMapping("/api/v1")
public class ConversationController {

    private final ChatService chatService;
    private final PersonalWorkspaceService personalWorkspaceService;
    private final CurrentUser currentUser;

    public ConversationController(ChatService chatService,
                                  PersonalWorkspaceService personalWorkspaceService,
                                  CurrentUser currentUser) {
        this.chatService = chatService;
        this.personalWorkspaceService = personalWorkspaceService;
        this.currentUser = currentUser;
    }

    @GetMapping("/conversations")
    public CursorPageVO<ConversationSummaryVO> conversations(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "30") int limit) {
        return personalWorkspaceService.conversations(currentUser.id(), cursor, limit);
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationVO> create(@Valid @RequestBody ConversationDTO request) {
        // 会话从商品进入且同一买家只保留一个有效会话，返回 201 便于客户端保存资源地址。
        ConversationVO conversation = chatService.create(currentUser.id(), request.listingId());
        return ResponseEntity.created(URI.create("/api/v1/conversations/" + conversation.id()))
                .body(conversation);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<MessageVO> send(@PathVariable long conversationId,
                                         @Valid @RequestBody ChatMessageDTO request) {
        // 消息先由 Service 落库并分配会话序号，事务提交后再通过 WebSocket 推送。
        return ResponseEntity.status(201)
                .body(chatService.send(currentUser.id(), conversationId, request));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public CursorPageVO<MessageVO> messages(@PathVariable long conversationId,
                                             @RequestParam(required = false) Long afterSequence,
                                             @RequestParam(defaultValue = "50") int limit) {
        // 断线重连使用 afterSequence 拉取缺失消息，不依赖 WebSocket 会话内缓存。
        return chatService.messages(currentUser.id(), conversationId, afterSequence, limit);
    }

    @PutMapping("/conversations/{conversationId}/read-cursor")
    public ResponseEntity<Map<String, Long>> markRead(@PathVariable long conversationId,
                                                      @RequestParam long sequence) {
        // 已读游标只能推进到会话当前最大序号，防止客户端传入未来序号影响未读统计。
        chatService.markRead(currentUser.id(), conversationId, sequence);
        return ResponseEntity.ok(Map.of("unread", chatService.unread(currentUser.id(), conversationId)));
    }

    @PutMapping("/listings/{listingId}/chat-setting")
    public ResponseEntity<Void> updateChatSetting(@PathVariable long listingId,
                                                  @RequestParam boolean enabled) {
        // 商品级开关只阻止创建新会话，已有订单履约会话仍允许继续沟通。
        chatService.updateListingChat(currentUser.id(), listingId, enabled);
        return ResponseEntity.noContent().build();
    }
}
