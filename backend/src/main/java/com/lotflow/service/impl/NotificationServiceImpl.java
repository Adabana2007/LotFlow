package com.lotflow.service.impl;

import com.lotflow.common.Cursor;
import com.lotflow.common.CursorPageVO;
import com.lotflow.entity.NotificationDO;
import com.lotflow.entity.UserDO;
import com.lotflow.entity.enums.DomainEnums.NotificationType;
import com.lotflow.mapper.NotificationMapper;
import com.lotflow.mapper.UserMapper;
import com.lotflow.service.NotificationService;
import com.lotflow.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 通知偏好过滤、站内通知落库和已读状态。 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_CONTENT_LENGTH = 500;

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void push(long userId, NotificationType type, String title, String content,
                     String resourceType, long resourceId, String bizKey) {
        UserDO user = userMapper.selectById(userId);
        if (user == null || !accepts(user, type)) {
            // 用户已关闭该类提醒时不落库，通知列表不能出现被明确拒绝的消息。
            return;
        }
        NotificationDO notification = new NotificationDO();
        notification.setUserId(userId);
        notification.setNotificationType(type);
        notification.setTitle(truncate(title, MAX_TITLE_LENGTH));
        notification.setContent(truncate(content, MAX_CONTENT_LENGTH));
        notification.setResourceType(resourceType);
        notification.setResourceId(resourceId);
        notification.setBizKey(bizKey);
        notificationMapper.insertIfAbsent(notification);
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<NotificationVO> list(long userId, String cursor, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        List<NotificationDO> rows = notificationMapper.selectPage(userId, Cursor.parse(cursor), limit + 1);
        boolean hasMore = rows.size() > limit;
        List<NotificationDO> selected = hasMore ? rows.subList(0, limit) : rows;
        String next = hasMore ? selected.getLast().getId().toString() : null;
        return new CursorPageVO<>(selected.stream().map(NotificationVO::from).toList(), next);
    }

    @Transactional(readOnly = true)
    @Override
    public long unreadCount(long userId) {
        return notificationMapper.countUnread(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void markRead(long userId, long notificationId) {
        // 条件更新同时校验归属和未读状态，重复点击不会改写其他用户的通知。
        notificationMapper.markRead(userId, notificationId);
    }

    private boolean accepts(UserDO user, NotificationType type) {
        Boolean enabled = switch (type) {
            case AUCTION -> user.getNotifyAuctionEnding();
            case ORDER -> user.getNotifyOrderPayment();
            case MESSAGE -> user.getNotifyMessages();
        };
        // 字段由数据库默认值保证非空；历史数据为空时按开启处理，避免静默丢弃通知。
        return !Boolean.FALSE.equals(enabled);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

}
