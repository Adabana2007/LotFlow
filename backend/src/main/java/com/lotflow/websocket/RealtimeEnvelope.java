package com.lotflow.websocket;

import java.time.OffsetDateTime;

/** WebSocket 统一事件信封。 */
public record RealtimeEnvelope(String type, String resourceId, long sequence,
                               OffsetDateTime occurredAt, Object data) {
}
