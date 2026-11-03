package com.lotflow.vo;

import com.lotflow.entity.NotificationDO;

import java.time.OffsetDateTime;

/** 站内通知响应。 */
public record NotificationVO(String id, String type, String title, String content,
                             String resourceType, String resourceId, boolean read,
                             OffsetDateTime occurredAt) {

    public static NotificationVO from(NotificationDO notification) {
        return new NotificationVO(notification.getId().toString(),
                notification.getNotificationType().name(), notification.getTitle(),
                notification.getContent(), notification.getResourceType(),
                notification.getResourceId().toString(),
                Boolean.TRUE.equals(notification.getIsRead()), notification.getGmtCreate());
    }
}
