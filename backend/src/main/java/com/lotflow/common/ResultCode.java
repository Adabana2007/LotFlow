package com.lotflow.common;

import org.springframework.http.HttpStatus;

/** 对外稳定错误码。 */
public enum ResultCode {
    INVALID_ARGUMENT("INVALID_ARGUMENT", "请求参数不合法", HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    UNAUTHORIZED("UNAUTHORIZED", "未认证", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "无权执行该操作", HttpStatus.FORBIDDEN),
    NOT_FOUND("NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND),
    CONFLICT("CONFLICT", "资源状态冲突", HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "幂等键已被不同请求使用", HttpStatus.CONFLICT),
    BUSINESS_RULE_VIOLATION("BUSINESS_RULE_VIOLATION", "业务规则不允许该操作", HttpStatus.UNPROCESSABLE_CONTENT),
    RATE_LIMITED("RATE_LIMITED", "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    PAYLOAD_TOO_LARGE("PAYLOAD_TOO_LARGE", "请求内容过大", HttpStatus.CONTENT_TOO_LARGE),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "依赖服务暂不可用", HttpStatus.SERVICE_UNAVAILABLE),
    AI_UNAVAILABLE("AI_UNAVAILABLE", "AI 服务暂不可用", HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR("INTERNAL_ERROR", "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String title;
    private final HttpStatus status;

    ResultCode(String code, String title, HttpStatus status) {
        this.code = code;
        this.title = title;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public HttpStatus status() {
        return status;
    }
}
