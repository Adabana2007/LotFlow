package com.lotflow.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** API 统一异常转换。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException exception) {
        return problem(exception.getResultCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("请求参数不合法");
        return problem(ResultCode.INVALID_ARGUMENT, detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        return problem(ResultCode.INVALID_ARGUMENT, exception.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException exception) {
        // 幂等键和 If-Match 是必填协议头：缺失属于客户端错误，不能被兜底处理器转成 500。
        return problem(ResultCode.INVALID_ARGUMENT, "缺少必填请求头: " + exception.getHeaderName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException exception) {
        return problem(ResultCode.INVALID_ARGUMENT, "缺少必填参数: " + exception.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return problem(ResultCode.INVALID_ARGUMENT, "参数类型不合法: " + exception.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException exception) {
        // 不回显原始报文：请求体可能含手机号和地址，解析异常文本也会泄露内部字段结构。
        return problem(ResultCode.INVALID_ARGUMENT, "请求体格式不合法");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return problem(ResultCode.METHOD_NOT_ALLOWED, ResultCode.METHOD_NOT_ALLOWED.title());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException exception) {
        // 未匹配到任何处理器时返回 404，避免访问错误路径被当成服务端故障。
        return problem(ResultCode.NOT_FOUND, ResultCode.NOT_FOUND.title());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException exception) {
        return problem(ResultCode.UNAUTHORIZED, ResultCode.UNAUTHORIZED.title());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        return problem(ResultCode.FORBIDDEN, ResultCode.FORBIDDEN.title());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception) {
        // 数据库唯一键或约束冲突属于业务并发冲突，只返回 409，不把 SQL 和约束名暴露给客户端。
        log.warn("database constraint rejected request, traceId={}", traceId());
        return problem(ResultCode.CONFLICT, "数据冲突，请刷新后重试");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnknown(Exception exception) {
        // 未预期异常只在服务端记录堆栈和 traceId，响应体仅返回统一错误码，避免泄露内部实现。
        log.error("unexpected error, traceId={}", traceId(), exception);
        return problem(ResultCode.INTERNAL_ERROR, ResultCode.INTERNAL_ERROR.title());
    }

    private ProblemDetail problem(ResultCode code, String detail) {
        // ProblemDetail 同时携带 HTTP 状态、业务 code 和 traceId，前端和排障人员都能定位同一错误。
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        problem.setType(java.net.URI.create("urn:lotflow:error:" + code.code().toLowerCase()));
        problem.setProperty("code", code.code());
        problem.setProperty("traceId", traceId());
        return problem;
    }

    private String traceId() {
        String traceId = MDC.get("traceId");
        return traceId == null ? "" : traceId;
    }
}
