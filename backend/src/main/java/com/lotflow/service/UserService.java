package com.lotflow.service;

import com.lotflow.dto.NotificationPreferencesDTO;
import com.lotflow.dto.UpdateProfileDTO;
import com.lotflow.vo.CreditVO;
import com.lotflow.vo.UserVO;

/** 用户资料与聊天默认设置。 */
public interface UserService {

    UserVO getCurrentUser(long userId);

    void updateChatEnabled(long userId, boolean enabled);

    void updateProfile(long userId, UpdateProfileDTO request);

    void updateNotifications(long userId, NotificationPreferencesDTO request);

    void updateAvatar(long userId, String objectKey);

    CreditVO getCredit(long userId);
}
