package com.lotflow.vo;

import com.lotflow.entity.UserDO;

/** 运营后台用户摘要。 */
public record AdminUserVO(String id, String phone, String displayName, String status,
                          String role, int creditScore, int completedReviewCount) {
    public static AdminUserVO from(UserDO user) {
        return new AdminUserVO(user.getId().toString(), user.getPhone(), user.getDisplayName(),
                user.getStatus().name(), user.getRoleCode().name(), user.getCreditScore(),
                user.getCompletedReviewCount());
    }
}
