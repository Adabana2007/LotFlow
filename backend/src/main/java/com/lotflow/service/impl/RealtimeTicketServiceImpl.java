package com.lotflow.service.impl;

import com.lotflow.service.RealtimeTicketService;

import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import com.lotflow.vo.RealtimeTicketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** WebSocket 一次性连接票据。 */
@Service
@RequiredArgsConstructor
public class RealtimeTicketServiceImpl implements RealtimeTicketService {

    private static final String KEY_PREFIX = "lotflow:realtime:ticket:";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    @Override
    public RealtimeTicketVO issue(long userId) {
        String ticket = UUID.randomUUID() + "." + UUID.randomUUID();
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + ticket, Long.toString(userId), TTL);
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "实时连接暂不可用", exception);
        }
        return new RealtimeTicketVO(ticket, Instant.now().plus(TTL));
    }

    @Override
    public Long consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        try {
            // GETDEL 让竞争的握手最多只有一个能拿到票据，消费后立即失效。
            String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + ticket);
            return value == null ? null : Long.parseLong(value);
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "实时连接暂不可用", exception);
        }
    }
}
