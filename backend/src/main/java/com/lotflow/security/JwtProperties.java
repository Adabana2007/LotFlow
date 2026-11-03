package com.lotflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** JWT 有效期与签发配置。 */
@ConfigurationProperties("lotflow.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}
