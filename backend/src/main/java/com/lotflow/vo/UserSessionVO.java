package com.lotflow.vo;

import java.time.Instant;

/** 当前账号通过 Refresh Token 存续的设备会话。 */
public record UserSessionVO(String id, String deviceName, Instant createdAt,
                            Instant lastUsedAt, boolean current) {
}
