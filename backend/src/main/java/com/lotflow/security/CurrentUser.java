package com.lotflow.security;

import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 当前登录用户身份读取。 */
@Component
public class CurrentUser {

    public long id() {
        // 业务接口只接受认证上下文中的 uid，不能信任客户端提交的 userId 参数。
        return optionalId().orElseThrow(() ->
                new BusinessException(ResultCode.UNAUTHORIZED, "请先登录"));
    }

    public Optional<Long> optionalId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        Number userId = jwt.getClaim("uid");
        return userId == null ? Optional.empty() : Optional.of(userId.longValue());
    }

    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 角色判断基于 JWT 转换后的 GrantedAuthority；细粒度资源归属仍由 Service 再次校验。
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
}
