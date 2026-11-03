package com.lotflow.service;

import com.lotflow.vo.UserSessionVO;

import java.util.List;

/** Redis 中的 Refresh Token 轮换与设备会话生命周期。 */
public interface SessionService {

    SessionIssue create(long userId, String deviceName);

    SessionIssue rotate(String rawRefreshToken);

    void revoke(String rawRefreshToken);

    List<UserSessionVO> list(long userId, String currentSessionId);

    void revoke(long userId, String sessionId);

    void revokeAll(long userId);

    record SessionIssue(long userId, String refreshToken, String sessionId) {
    }
}
