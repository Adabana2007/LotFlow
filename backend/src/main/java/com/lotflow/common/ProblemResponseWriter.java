package com.lotflow.common;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

/** 过滤器阶段无法进入 ControllerAdvice，因此在这里生成同样的 ProblemDetail。 */
@Component
@RequiredArgsConstructor
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ResultCode resultCode, String detail,
                      Map<String, Object> properties) throws IOException {
        // 过滤器和安全链路可能早于 Spring MVC 执行，因此这里手工生成与全局异常处理一致的 ProblemDetail。
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(resultCode.status(), detail);
        problem.setTitle(resultCode.title());
        problem.setType(java.net.URI.create("urn:lotflow:error:"
                + resultCode.code().toLowerCase(java.util.Locale.ROOT)));
        problem.setProperty("code", resultCode.code());
        // traceId 同时进入响应和日志，用户报错时可以据此定位过滤器层拒绝原因。
        problem.setProperty("traceId", traceId());
        properties.forEach(problem::setProperty);
        response.setStatus(resultCode.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private String traceId() {
        String traceId = MDC.get("traceId");
        return traceId == null ? "" : traceId;
    }
}
