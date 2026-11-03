package com.lotflow.config;

import com.lotflow.security.ApiSecurityExceptionHandler;
import com.lotflow.security.ActiveUserFilter;
import com.lotflow.security.RateLimitFilter;
import com.lotflow.security.RequestSizeLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;

/** 无状态资源服务器；资源和会话访问权限由 Service/方法安全再次校验。 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ApiSecurityExceptionHandler securityExceptionHandler,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter,
                                                   ActiveUserFilter activeUserFilter,
                                                   RequestSizeLimitFilter requestSizeLimitFilter,
                                                   RateLimitFilter rateLimitFilter)
            throws Exception {
        // API 使用 Bearer Access Token 而不是 Cookie 会话，因此采用 STATELESS 并关闭 CSRF。
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // WebSocket 握手在拦截器里用一次性票据鉴权，因此这里不依赖 Bearer Token。
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users", "/api/v1/auth/tokens",
                                "/api/v1/auth/tokens/refresh").permitAll()
                        // 本地媒体上传和读取都由一次性短时票据授权，不把 Access Token 放进资源 URL。
                        .requestMatchers(HttpMethod.PUT, "/api/v1/media/uploads/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/media/images/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/media/avatars/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/listings", "/api/v1/listings/*",
                                "/api/v1/listings/*/images/*/url",
                                "/api/v1/auctions", "/api/v1/auctions/*", "/api/v1/auctions/*/bids",
                                "/api/v1/search/listings").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .addFilterAfter(activeUserFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(requestSizeLimitFilter, ActiveUserFilter.class)
                .addFilterAfter(rateLimitFilter, RequestSizeLimitFilter.class);
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<ActiveUserFilter> activeUserFilterRegistration(
            ActiveUserFilter filter) {
        return disabledServletRegistration(filter);
    }

    @Bean
    public FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilterRegistration(
            RequestSizeLimitFilter filter) {
        return disabledServletRegistration(filter);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        return disabledServletRegistration(filter);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 用户密码只保存 BCrypt 哈希；注册和登录都通过 Spring Security PasswordEncoder 处理。
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object roles = jwt.getClaim("roles");
            if (roles instanceof java.util.Collection<?> collection) {
                return collection.stream()
                        .map(String::valueOf)
                        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
            }
            return java.util.List.of();
        });
        return converter;
    }

    private <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disabledServletRegistration(
            T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
