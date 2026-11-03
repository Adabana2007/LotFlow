package com.lotflow.service;

import com.lotflow.common.CursorPageVO;
import com.lotflow.entity.enums.DomainEnums.NotificationType;
import com.lotflow.vo.NotificationVO;

/** 站内通知投递与查询。 */
public interface NotificationService {

    /**
     * 按用户通知偏好投递一条通知；偏好关闭时不写库。
     * 同一 bizKey 重复投递只保留第一条。
     */
    void push(long userId, NotificationType type, String title, String content,
              String resourceType, long resourceId, String bizKey);

    CursorPageVO<NotificationVO> list(long userId, String cursor, int requestedLimit);

    long unreadCount(long userId);

    void markRead(long userId, long notificationId);
}
