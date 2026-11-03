package com.lotflow.security;

import com.lotflow.common.ProblemResponseWriter;
import com.lotflow.common.ResultCode;
import com.lotflow.config.HttpProtectionProperties;
import com.lotflow.service.OperationMetricsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/** 按认证、写请求和查询分组限流，多实例共享 Redis 计数。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "lotflow:rate:";

    private final HttpProtectionProperties properties;
    private final RedisRateLimitService rateLimitService;
    private final OperationMetricsService metrics;
    private final ProblemResponseWriter problemWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.rateLimitEnabled()
                || !request.getRequestURI().startsWith("/api/")
                || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        LimitGroup group = group(request);
        String actor = actor(request);
        String key = KEY_PREFIX + group.name().toLowerCase(Locale.ROOT) + ":" + actor;
        try {
            long retryAfterMillis = rateLimitService.acquire(key, group.limit(properties),
                    group.window(properties));
            if (retryAfterMillis > 0) {
                long retryAfterSeconds = Math.max(1L,
                        (retryAfterMillis + 999L) / 1000L);
                response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
                metrics.recordRateLimited(group.name());
                problemWriter.write(response, ResultCode.RATE_LIMITED,
                        "请求过于频繁，请稍后重试",
                        Map.of("retryAfterSeconds", retryAfterSeconds));
                return;
            }
        } catch (RuntimeException exception) {
            if (!properties.redisFailOpen()) {
                // 安全敏感环境可以关闭 fail-open；限流依赖不可用时直接拒绝请求。
                problemWriter.write(response, ResultCode.SERVICE_UNAVAILABLE,
                        "限流服务暂不可用", Map.of());
                return;
            }
            // 默认选择 fail-open，避免 Redis 短暂故障把全部公开 API 一起打挂。
            log.warn("rate limiter unavailable, request allowed by fail-open policy");
        }
        filterChain.doFilter(request, response);
    }

    private LimitGroup group(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/api/v1/auth/tokens")
                || path.equals("/api/v1/auth/tokens/refresh")
                || (path.equals("/api/v1/users") && HttpMethod.POST.matches(request.getMethod()))) {
            return LimitGroup.AUTHENTICATION;
        }
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return LimitGroup.WRITE;
        }
        return LimitGroup.GENERAL;
    }

    private String actor(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return "user-" + authentication.getName();
        }
        return "ip-" + request.getRemoteAddr().replace(':', '_');
    }

    private enum LimitGroup {
        AUTHENTICATION,
        WRITE,
        GENERAL;

        int limit(HttpProtectionProperties properties) {
            return switch (this) {
                case AUTHENTICATION -> properties.authenticationRequests();
                case WRITE -> properties.writeRequests();
                case GENERAL -> properties.generalRequests();
            };
        }

        Duration window(HttpProtectionProperties properties) {
            return switch (this) {
                case AUTHENTICATION -> properties.authenticationWindow();
                case WRITE -> properties.writeWindow();
                case GENERAL -> properties.generalWindow();
            };
        }
    }
}
