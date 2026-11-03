package com.lotflow.vo;

import com.lotflow.entity.UserDO;

import java.util.List;

/** 当前用户的公开资料和通知偏好。 */
public record UserVO(String id, String phone, String displayName, String status,
                     int creditScore, boolean chatEnabled, List<String> roles,
                     String avatarUrl, boolean notifyAuctionEnding,
                     boolean notifyOrderPayment, boolean notifyMessages) {

    public static UserVO from(UserDO user, List<String> roles) {
        return new UserVO(user.getId().toString(), user.getPhone(), user.getDisplayName(),
                user.getStatus().name(), user.getCreditScore(),
                Boolean.TRUE.equals(user.getChatEnabled()), roles, avatarUrl(user),
                Boolean.TRUE.equals(user.getNotifyAuctionEnding()),
                Boolean.TRUE.equals(user.getNotifyOrderPayment()),
                Boolean.TRUE.equals(user.getNotifyMessages()));
    }

    public static UserVO from(UserDO user) {
        return from(user, List.of(user.getRoleCode().name()));
    }

    private static String avatarUrl(UserDO user) {
        if (user.getAvatarObjectKey() == null || user.getAvatarObjectKey().isBlank()) {
            return null;
        }
        // 头像 URL 只暴露用户编号，实际文件路径和对象键不会进入浏览器。
        return "/api/v1/media/avatars/" + user.getId();
    }
}
