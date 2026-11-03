package com.lotflow.websocket;

import com.lotflow.service.ChatService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/** WebSocket 连接身份和订阅权限校验。 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final ChatService chatService;

    public WebSocketAuthInterceptor(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() == StompCommand.CONNECT) {
            Object userId = accessor.getSessionAttributes() == null ? null
                    : accessor.getSessionAttributes()
                    .get(WebSocketTicketHandshakeInterceptor.USER_ID_ATTRIBUTE);
            if (userId == null) {
                throw new IllegalArgumentException("WebSocket authentication required");
            }
            Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                    userId.toString(), null, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
            accessor.setUser(authentication);
        }
        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        // 订阅必须声明目标频道：缺少或空白 destination 的帧默认拒绝，不能默认放行。
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("subscription destination required");
        }
        // 拍卖行情是公开频道；聊天频道还必须证明当前用户属于该会话。
        if (destination.startsWith("/topic/auctions/")) {
            return;
        }
        // 身份取握手阶段写入的 session attribute：票据握手没有 Principal，
        // accessor.getUser() 在 SUBSCRIBE 帧恒为 null，用它判断会让所有会话订阅失败。
        Object userId = accessor.getSessionAttributes() == null ? null
                : accessor.getSessionAttributes()
                .get(WebSocketTicketHandshakeInterceptor.USER_ID_ATTRIBUTE);
        if (destination.startsWith("/topic/conversations/") && userId != null) {
            long conversationId = Long.parseLong(destination.substring("/topic/conversations/".length()));
            if (!chatService.canAccess(conversationId, Long.parseLong(userId.toString()))) {
                throw new IllegalArgumentException("conversation subscription forbidden");
            }
            return;
        }
        throw new IllegalArgumentException("unsupported subscription destination");
    }
}
