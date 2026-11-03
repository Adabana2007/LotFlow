package com.lotflow.service;

import com.lotflow.dto.LoginDTO;
import com.lotflow.dto.PasswordChangeDTO;
import com.lotflow.dto.RegisterUserDTO;
import com.lotflow.vo.TokenVO;

/** 注册、登录和刷新令牌管理。 */
public interface AuthService {

    long register(RegisterUserDTO request);

    TokenVO login(LoginDTO request);

    TokenVO refresh(String rawToken);

    void revoke(String rawToken);

    void changePassword(long userId, PasswordChangeDTO request);
}
