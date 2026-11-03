package com.lotflow.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/** 多实例共享 Redis 限流计数。 */
@Service
public class RedisRateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public RedisRateLimitService(StringRedisTemplate redisTemplate,
                                 @Qualifier("rateLimitScript")
                                 DefaultRedisScript<Long> script) {
        this.redisTemplate = redisTemplate;
        this.script = script;
    }

    public long acquire(String key, int limit, Duration window) {
        // Lua 在 Redis 内完成计数和过期判断，脚本返回 0 表示放行，返回正数表示需要等待的毫秒数。
        Long retryAfter = redisTemplate.execute(script, List.of(key),
                Long.toString(window.toMillis()), Integer.toString(limit));
        if (retryAfter == null) {
            throw new IllegalStateException("rate limit script returned no result");
        }
        return retryAfter;
    }
}
