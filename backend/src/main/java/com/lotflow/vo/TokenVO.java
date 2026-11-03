package com.lotflow.vo;

import java.time.Instant;

/** Access Token、轮换后的 Refresh Token 与设备会话标识。 */
public record TokenVO(String accessToken, String refreshToken, Instant accessTokenExpiresAt,
                      String sessionId) {
}
