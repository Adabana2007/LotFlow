package com.lotflow.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 实时事件跨实例发布。 */
@Component
@RequiredArgsConstructor
public class RealtimePublisher {

    public static final String CHANNEL = "lotflow:realtime";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(String destination, String type, String resourceId,
                        long sequence, Object data) {
        RealtimeEnvelope envelope = new RealtimeEnvelope(type, resourceId, sequence,
                OffsetDateTime.now(ZoneOffset.UTC), data);
        try {
            // Redis Pub/Sub 把事件广播给所有实例，再由本地监听器只推给当前实例的 STOMP 连接。
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(
                    new RedisEnvelope(destination, envelope)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize realtime event", exception);
        }
    }

    public record RedisEnvelope(String destination, RealtimeEnvelope envelope) {
    }
}
