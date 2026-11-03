package com.lotflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Redis Lua 脚本配置。 */
@Configuration
public class RedisScriptConfig {

    @Bean
    public DefaultRedisScript<Long> seckillReserveScript() {
        // 预占脚本把库存判断和占位写入合并为一个 Redis 原子动作，避免多个请求同时看到剩余库存。
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/seckill_reserve.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> seckillReleaseScript() {
        // 释放脚本校验预占 token，只允许原预占释放库存，避免失败请求误删后续用户的占位。
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/seckill_release.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> rateLimitScript() {
        // 限流脚本在 Redis 内完成固定窗口计数，多个应用实例共享同一 key 的请求次数。
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/rate_limit.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> knowledgeIndexUnlockScript() {
        // 解锁脚本校验持有者 token，避免锁过期后误删另一个实例刚取得的重建锁。
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/knowledge_index_unlock.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
