package com.lotflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotflow.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import lombok.RequiredArgsConstructor;

/** 安全过滤链异常响应。 */
@Component
@RequiredArgsConstructor
public class ApiSecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        write(response, ResultCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(response, ResultCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ResultCode code) throws IOException {
        // 认证和授权失败也使用与 Controller 一致的 ProblemDetail，避免浏览器收到默认登录页或空响应。
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(code.status(), code.title());
        detail.setTitle(code.title());
        detail.setProperty("code", code.code());
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), detail);
    }
}
