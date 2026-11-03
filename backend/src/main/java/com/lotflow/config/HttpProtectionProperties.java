package com.lotflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 对外接口的应用层限流和请求体大小边界。 */
@ConfigurationProperties("lotflow.http")
public record HttpProtectionProperties(
        boolean rateLimitEnabled,
        int authenticationRequests,
        Duration authenticationWindow,
        int writeRequests,
        Duration writeWindow,
        int generalRequests,
        Duration generalWindow,
        boolean redisFailOpen,
        long maxRequestBytes
) {
}
