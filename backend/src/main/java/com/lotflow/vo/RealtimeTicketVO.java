package com.lotflow.vo;

import java.time.Instant;

/** WebSocket 一次性连接票据。 */
public record RealtimeTicketVO(String ticket, Instant expiresAt) {
}
