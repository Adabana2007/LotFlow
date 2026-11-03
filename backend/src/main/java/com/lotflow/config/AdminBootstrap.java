package com.lotflow.config;

import com.lotflow.entity.UserDO;
import com.lotflow.entity.enums.DomainEnums.RoleCode;
import com.lotflow.entity.enums.DomainEnums.UserStatus;
import com.lotflow.mapper.UserMapper;
import com.lotflow.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 部署环境管理员初始化。 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap {

    private final AdminProperties properties;
    private final UserMapper userMapper;
    private final WalletService walletService;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(rollbackFor = Exception.class)
    public void initialize() {
        // 管理员只能通过部署环境变量初始化；缺少手机号或密码时保持无管理员状态，不开放公开注册入口。
        if (properties.phone() == null || properties.phone().isBlank()
                || properties.password() == null || properties.password().isBlank()) {
            return;
        }
        UserDO user = userMapper.selectByPhone(properties.phone());
        if (user == null) {
            user = new UserDO();
            user.setUsername(properties.phone());
            user.setPhone(properties.phone());
            user.setDisplayName(properties.nickname());
            user.setPasswordHash(passwordEncoder.encode(properties.password()));
            user.setRoleCode(RoleCode.ADMIN);
            user.setStatus(UserStatus.ACTIVE);
            user.setChatEnabled(false);
            user.setCompletedReviewCount(0);
            user.setCreditScore(100);
            userMapper.insert(user);
            // 管理员角色直接属于用户行，用户和平台钱包必须同事务创建。
            walletService.createUserAccount(user.getId());
            return;
        }
        if (user.getRoleCode() != RoleCode.ADMIN) {
            // 手机号已被普通用户注册时拒绝启动，避免环境变量把既有账号静默提升为管理员。
            throw new IllegalStateException(
                    "ADMIN_PHONE belongs to an existing non-admin account");
        }
    }
}
