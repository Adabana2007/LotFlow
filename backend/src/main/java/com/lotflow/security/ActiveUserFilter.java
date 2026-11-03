package com.lotflow.security;

import com.lotflow.entity.UserDO;
import com.lotflow.entity.enums.DomainEnums.UserStatus;
import com.lotflow.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 受保护请求的账号状态校验。 */
@Component
@RequiredArgsConstructor
public class ActiveUserFilter extends OncePerRequestFilter {

    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Number userId = jwtAuthentication.getToken().getClaim("uid");
            UserDO user = userId == null ? null : userMapper.selectById(userId.longValue());
            if (user == null || user.getStatus() != UserStatus.ACTIVE) {
                // JWT 可能仍在有效期内，但管理员禁用账号后必须立即失效，因此每次请求回查账号状态并清空上下文。
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getWriter().write("{\"title\":\"账号不可用\",\"status\":403,"
                        + "\"code\":\"FORBIDDEN\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
