package com.lotflow.service.impl;

import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import com.lotflow.security.JwtProperties;
import com.lotflow.service.SessionService;
import com.lotflow.vo.UserSessionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Redis 中的可轮换 Refresh Token、设备会话索引和撤销边界。 */
@Service
@RequiredArgsConstructor
public class RedisSessionServiceImpl implements SessionService {

    private static final String REFRESH_PREFIX = "lotflow:auth:refresh:";
    private static final String SESSION_PREFIX = "lotflow:auth:session:";
    private static final String USER_SESSIONS_PREFIX = "lotflow:auth:user-sessions:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    @Override
    public SessionIssue create(long userId, String deviceName) {
        String sessionId = UUID.randomUUID().toString();
        return createAndStore(userId, sessionId, normalizeDeviceName(deviceName));
    }

    @Override
    public SessionIssue rotate(String rawRefreshToken) {
        String sessionId = parseSessionId(rawRefreshToken);
        try {
            String mapping = redisTemplate.opsForValue().getAndDelete(refreshKey(hash(rawRefreshToken)));
            SessionIdentity identity = parseIdentity(mapping, sessionId);
            if (identity == null) {
                throw unauthorized();
            }
            Map<String, String> session = redisTemplate.<String, String>opsForHash()
                    .entries(sessionKey(sessionId));
            if (session.isEmpty()
                    || !Long.toString(identity.userId()).equals(session.get("userId"))
                    || !hash(rawRefreshToken).equals(session.get("tokenHash"))) {
                throw unauthorized();
            }
            return rotateAndStore(identity.userId(), sessionId, session.get("deviceName"));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "登录会话暂不可用", exception);
        }
    }

    @Override
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String sessionId;
        try {
            sessionId = parseSessionId(rawRefreshToken);
        } catch (BusinessException exception) {
            return;
        }
        try {
            String mapping = redisTemplate.opsForValue().getAndDelete(refreshKey(hash(rawRefreshToken)));
            SessionIdentity identity = parseIdentity(mapping, sessionId);
            if (identity != null) {
                redisTemplate.delete(sessionKey(sessionId));
                redisTemplate.opsForSet().remove(userSessionsKey(identity.userId()), sessionId);
            }
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "登录会话暂不可用", exception);
        }
    }

    @Override
    public List<UserSessionVO> list(long userId, String currentSessionId) {
        try {
            Set<String> ids = redisTemplate.opsForSet().members(userSessionsKey(userId));
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            List<UserSessionVO> sessions = new ArrayList<>(ids.size());
            for (String sessionId : ids) {
                Map<String, String> values = redisTemplate.<String, String>opsForHash()
                        .entries(sessionKey(sessionId));
                if (values.isEmpty() || !Long.toString(userId).equals(values.get("userId"))) {
                    redisTemplate.opsForSet().remove(userSessionsKey(userId), sessionId);
                    continue;
                }
                sessions.add(new UserSessionVO(sessionId, values.getOrDefault("deviceName", "未命名设备"),
                        instant(values.get("createdAt")), instant(values.get("lastUsedAt")),
                        sessionId.equals(currentSessionId)));
            }
            return sessions.stream()
                    .sorted(Comparator.comparing(UserSessionVO::lastUsedAt).reversed())
                    .toList();
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "登录会话暂不可用", exception);
        }
    }

    @Override
    public void revoke(long userId, String sessionId) {
        try {
            Map<String, String> session = redisTemplate.<String, String>opsForHash()
                    .entries(sessionKey(sessionId));
            if (session.isEmpty() || !Long.toString(userId).equals(session.get("userId"))) {
                throw new BusinessException(ResultCode.NOT_FOUND, "登录会话不存在");
            }
            String tokenHash = session.get("tokenHash");
            if (tokenHash != null) {
                redisTemplate.delete(REFRESH_PREFIX + tokenHash);
            }
            redisTemplate.delete(sessionKey(sessionId));
            redisTemplate.opsForSet().remove(userSessionsKey(userId), sessionId);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "登录会话暂不可用", exception);
        }
    }

    @Override
    public void revokeAll(long userId) {
        try {
            Set<String> ids = redisTemplate.opsForSet().members(userSessionsKey(userId));
            if (ids == null) {
                return;
            }
            for (String sessionId : ids) {
                Map<String, String> session = redisTemplate.<String, String>opsForHash()
                        .entries(sessionKey(sessionId));
                String tokenHash = session.get("tokenHash");
                if (tokenHash != null) {
                    redisTemplate.delete(REFRESH_PREFIX + tokenHash);
                }
                redisTemplate.delete(sessionKey(sessionId));
            }
            redisTemplate.delete(userSessionsKey(userId));
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "登录会话暂不可用", exception);
        }
    }

    private SessionIssue createAndStore(long userId, String sessionId, String deviceName) {
        try {
            String rawRefreshToken = newRefreshToken(sessionId);
            Instant now = Instant.now();
            Duration ttl = jwtProperties.refreshTokenTtl();
            redisTemplate.opsForValue().set(refreshKey(hash(rawRefreshToken)),
                    userId + ":" + sessionId, ttl);
            redisTemplate.opsForHash().putAll(sessionKey(sessionId), Map.of(
                    "userId", Long.toString(userId),
                    "tokenHash", hash(rawRefreshToken),
                    "deviceName", deviceName,
                    "createdAt", Long.toString(now.toEpochMilli()),
                    "lastUsedAt", Long.toString(now.toEpochMilli())));
            redisTemplate.expire(sessionKey(sessionId), ttl);
            redisTemplate.opsForSet().add(userSessionsKey(userId), sessionId);
            redisTemplate.expire(userSessionsKey(userId), ttl.plusDays(1));
            return new SessionIssue(userId, rawRefreshToken, sessionId);
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "登录会话暂不可用", exception);
        }
    }

    private SessionIssue rotateAndStore(long userId, String sessionId, String deviceName) {
        String rawRefreshToken = newRefreshToken(sessionId);
        Duration ttl = jwtProperties.refreshTokenTtl();
        redisTemplate.opsForValue().set(refreshKey(hash(rawRefreshToken)),
                userId + ":" + sessionId, ttl);
        redisTemplate.opsForHash().put(sessionKey(sessionId), "tokenHash", hash(rawRefreshToken));
        redisTemplate.opsForHash().put(sessionKey(sessionId), "lastUsedAt",
                Long.toString(Instant.now().toEpochMilli()));
        redisTemplate.expire(sessionKey(sessionId), ttl);
        redisTemplate.opsForSet().add(userSessionsKey(userId), sessionId);
        redisTemplate.expire(userSessionsKey(userId), ttl.plusDays(1));
        return new SessionIssue(userId, rawRefreshToken, sessionId);
    }

    private String newRefreshToken(String sessionId) {
        // sessionId 前缀让注销请求无需 Redis 扫描就能定位会话；真实随机秘密仍以完整摘要作为键。
        return sessionId + "." + UUID.randomUUID() + "." + UUID.randomUUID();
    }

    private String parseSessionId(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw unauthorized();
        }
        int separator = rawRefreshToken.indexOf('.');
        if (separator <= 0) {
            throw unauthorized();
        }
        try {
            UUID.fromString(rawRefreshToken.substring(0, separator));
            return rawRefreshToken.substring(0, separator);
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private SessionIdentity parseIdentity(String mapping, String sessionId) {
        if (mapping == null) {
            return null;
        }
        String[] parts = mapping.split(":", 2);
        if (parts.length != 2 || !sessionId.equals(parts[1])) {
            return null;
        }
        try {
            return new SessionIdentity(Long.parseLong(parts[0]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeDeviceName(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return "未命名设备";
        }
        String normalized = deviceName.replaceAll("[\\p{Cntrl}]", "").trim();
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private Instant instant(String value) {
        try {
            return value == null ? Instant.EPOCH : Instant.ofEpochMilli(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return Instant.EPOCH;
        }
    }

    private String refreshKey(String tokenHash) {
        return REFRESH_PREFIX + tokenHash;
    }

    private String sessionKey(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    private String userSessionsKey(long userId) {
        return USER_SESSIONS_PREFIX + userId;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private BusinessException unauthorized() {
        return new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌无效或已过期");
    }

    private record SessionIdentity(long userId) {
    }
}
