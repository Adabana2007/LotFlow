package com.lotflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import com.lotflow.dto.NotificationPreferencesDTO;
import com.lotflow.dto.UpdateProfileDTO;
import com.lotflow.entity.AuditLogDO;
import com.lotflow.entity.UserDO;
import com.lotflow.mapper.AuditLogMapper;
import com.lotflow.mapper.UserMapper;
import com.lotflow.service.UserService;
import com.lotflow.vo.CreditRecordVO;
import com.lotflow.vo.CreditVO;
import com.lotflow.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户资料、头像、通知偏好、聊天默认设置和信用审计查询。 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final AuditLogMapper auditLogMapper;

    @Transactional(readOnly = true)
    @Override
    public UserVO getCurrentUser(long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return UserVO.from(user);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateChatEnabled(long userId, boolean enabled) {
        UserDO user = requireUserForUpdate(userId);
        user.setChatEnabled(enabled);
        // 账号开关只作为商品未单独配置时的默认值，不删除既有履约会话。
        userMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateProfile(long userId, UpdateProfileDTO request) {
        UserDO user = requireUserForUpdate(userId);
        user.setDisplayName(request.displayName().trim());
        userMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateNotifications(long userId, NotificationPreferencesDTO request) {
        UserDO user = requireUserForUpdate(userId);
        // 通知偏好只影响后续提醒，不改变订单截止时间、资金状态或强制执行任务。
        user.setNotifyAuctionEnding(request.auctionEnding());
        user.setNotifyOrderPayment(request.orderPayment());
        user.setNotifyMessages(request.messages());
        userMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateAvatar(long userId, String objectKey) {
        UserDO user = requireUserForUpdate(userId);
        user.setAvatarObjectKey(objectKey);
        userMapper.updateById(user);
    }

    @Transactional(readOnly = true)
    @Override
    public CreditVO getCredit(long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        var records = auditLogMapper.selectList(new LambdaQueryWrapper<AuditLogDO>()
                .eq(AuditLogDO::getSubjectUserId, userId)
                .eq(AuditLogDO::getAction, "CREDIT_CHANGED")
                .orderByDesc(AuditLogDO::getId)
                .last("limit 50"));
        return new CreditVO(Long.toString(userId), user.getCreditScore(),
                records.stream().map(CreditRecordVO::from).toList());
    }

    private UserDO requireUserForUpdate(long userId) {
        UserDO user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
