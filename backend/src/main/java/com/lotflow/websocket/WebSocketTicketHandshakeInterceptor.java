package com.lotflow.websocket;

import com.lotflow.common.BusinessException;
import com.lotflow.service.RealtimeTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/** WebSocket 握手票据校验。 */
@Component
@RequiredArgsConstructor
public class WebSocketTicketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTRIBUTE = "lotflowUserId";

    private final RealtimeTicketService ticketService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams();
        try {
            // 票据由 Redis GETDEL 原子消费；同一 ticket 即使被并发重放，最多只有一个握手成功。
            Long userId = ticketService.consume(query.getFirst("ticket"));
            if (userId == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(USER_ID_ATTRIBUTE, userId);
            return true;
        } catch (BusinessException exception) {
            // Redis 故障说明无法确认票据，返回 503 让客户端重试，不能当作无票据的 401。
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
