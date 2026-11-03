package com.lotflow.service.impl;

import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import com.lotflow.dto.LoginDTO;
import com.lotflow.dto.PasswordChangeDTO;
import com.lotflow.dto.RegisterUserDTO;
import com.lotflow.entity.UserDO;
import com.lotflow.entity.enums.DomainEnums.RoleCode;
import com.lotflow.entity.enums.DomainEnums.UserStatus;
import com.lotflow.mapper.UserMapper;
import com.lotflow.security.JwtTokenService;
import com.lotflow.service.AuthService;
import com.lotflow.service.SessionService;
import com.lotflow.service.WalletService;
import com.lotflow.vo.TokenVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 注册、密码登录、设备会话轮换和密码修改。 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final WalletService walletService;
    private final SessionService sessionService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public long register(RegisterUserDTO request) {
        String username = request.username() == null || request.username().isBlank()
                ? request.phone() : request.username().trim();
        if (userMapper.selectByAccount(request.phone()) != null
                || userMapper.selectByUsername(username) != null) {
            // 预检查改善错误提示，最终唯一性仍由手机号和用户名的数据库唯一索引兜底。
            throw new BusinessException(ResultCode.CONFLICT, "手机号或用户名已注册");
        }
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPhone(request.phone());
        // 数据库只保存 BCrypt 哈希，接口、日志和查询都不能接触用户明文密码。
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.nickname());
        user.setRoleCode(RoleCode.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setChatEnabled(false);
        user.setCompletedReviewCount(0);
        user.setCreditScore(100);
        userMapper.insert(user);

        // 用户行和平台虚拟钱包是交易前置条件，必须一起提交或一起回滚。
        walletService.createUserAccount(user.getId());
        return user.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public TokenVO login(LoginDTO request) {
        // 字段名 phone 保留给现有客户端兼容，查询层同时匹配 username 和 phone 两个唯一账号标识。
        UserDO user = userMapper.selectByAccount(request.phone());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名、手机号或密码错误");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            // JWT 仍有效也禁止受限账号重新签发令牌，ActiveUserFilter 继续负责存量令牌失效。
            throw new BusinessException(ResultCode.FORBIDDEN, "账号当前不可登录");
        }
        return issueTokens(user, request.deviceName());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public TokenVO refresh(String rawToken) {
        // Redis 8 的 GETDEL 在设备会话服务内原子消费旧令牌，并把新令牌绑定到同一 sessionId。
        SessionService.SessionIssue rotated = sessionService.rotate(rawToken);
        UserDO user = userMapper.selectById(rotated.userId());
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号当前不可登录");
        }
        JwtTokenService.TokenIssue access = jwtTokenService.issue(
                user.getId(), List.of(user.getRoleCode().name()));
        return new TokenVO(access.token(), rotated.refreshToken(), access.expiresAt(),
                rotated.sessionId());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void revoke(String rawToken) {
        // 注销只撤销当前设备会话；其它设备签发过的 Refresh Token 不受影响。
        sessionService.revoke(rawToken);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void changePassword(long userId, PasswordChangeDTO request) {
        UserDO user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "新密码不能与当前密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
        // 密码修改后所有设备都必须重新登录，防止旧 Refresh Token 继续延长已失效凭据的生命周期。
        sessionService.revokeAll(userId);
    }

    private TokenVO issueTokens(UserDO user, String deviceName) {
        JwtTokenService.TokenIssue access = jwtTokenService.issue(
                user.getId(), List.of(user.getRoleCode().name()));
        SessionService.SessionIssue session = sessionService.create(user.getId(), deviceName);
        // Redis 只保存 Refresh Token 摘要和设备索引，明文仅在本响应中出现一次。
        return new TokenVO(access.token(), session.refreshToken(), access.expiresAt(),
                session.sessionId());
    }
}
