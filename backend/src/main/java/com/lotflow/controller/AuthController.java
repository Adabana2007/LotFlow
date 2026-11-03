package com.lotflow.controller;

import com.lotflow.dto.LoginDTO;
import com.lotflow.dto.PasswordChangeDTO;
import com.lotflow.dto.RefreshTokenDTO;
import com.lotflow.dto.RegisterUserDTO;
import com.lotflow.security.CurrentUser;
import com.lotflow.service.AuthService;
import com.lotflow.service.SessionService;
import com.lotflow.vo.TokenVO;
import com.lotflow.vo.UserSessionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** 注册、登录、密码、Refresh Token 和设备会话接口。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final CurrentUser currentUser;

    @PostMapping("/users")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterUserDTO request) {
        long userId = authService.register(request);
        // 注册创建的是新用户资源，返回 201 和 Location；密码哈希和内部实体不进入响应体。
        return ResponseEntity.created(URI.create("/api/v1/users/" + userId)).build();
    }

    @PostMapping("/auth/tokens")
    public TokenVO login(@Valid @RequestBody LoginDTO request) {
        // 登录成功后同时签发短期 Access Token 和可轮换 Refresh Token，客户端只拿明文令牌。
        return authService.login(request);
    }

    @PostMapping("/auth/tokens/refresh")
    public TokenVO refresh(@Valid @RequestBody RefreshTokenDTO request) {
        // 刷新动作会撤销旧 Refresh Token 并返回新令牌，重复使用旧令牌将被拒绝。
        return authService.refresh(request.refreshToken());
    }

    @DeleteMapping("/auth/tokens/current")
    public ResponseEntity<Void> revoke(@Valid @RequestBody RefreshTokenDTO request) {
        // 注销只撤销当前刷新令牌并返回 204，不删除用户或历史令牌审计记录。
        authService.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/auth/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeDTO request) {
        authService.changePassword(currentUser.id(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/auth/sessions")
    public List<UserSessionVO> sessions(
            @RequestParam(required = false) String currentSessionId) {
        return sessionService.list(currentUser.id(), currentSessionId);
    }

    @DeleteMapping("/auth/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId) {
        sessionService.revoke(currentUser.id(), sessionId);
        return ResponseEntity.noContent().build();
    }
}
