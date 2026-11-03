package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lotflow.entity.enums.DomainEnums.NotificationType;
import lombok.Getter;
import lombok.Setter;

/** 站内通知。 */
@Getter
@Setter
@TableName("notification")
public class NotificationDO extends BaseDO {
    private Long userId;
    private NotificationType notificationType;
    private String title;
    private String content;
    private String resourceType;
    private Long resourceId;
    private String bizKey;
    private Boolean isRead;
}
