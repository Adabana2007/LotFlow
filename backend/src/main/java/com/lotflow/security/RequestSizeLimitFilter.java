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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/** API 请求体大小限制过滤器。 */
@Component
@RequiredArgsConstructor
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private static final String MEDIA_UPLOAD_PATH = "/api/v1/media/uploads/";

    private final HttpProtectionProperties properties;
    private final OperationMetricsService metrics;
    private final ProblemResponseWriter problemWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        // 图片上传由媒体服务按真实读取字节限制 10 MiB；通用 JSON 上限不能误伤合法的二进制文件。
        return !requestUri.startsWith("/api/") || requestUri.startsWith(MEDIA_UPLOAD_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > properties.maxRequestBytes()) {
            // 请求体超过业务上限时在进入 Controller 前直接返回 413，避免大 JSON 占用线程和堆内存。
            metrics.recordRequestRejected("payload-too-large");
            problemWriter.write(response, ResultCode.PAYLOAD_TOO_LARGE,
                    "请求内容超过允许大小", Map.of("maxBytes", properties.maxRequestBytes()));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
