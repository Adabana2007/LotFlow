package com.lotflow.controller;

import com.lotflow.common.CursorPageVO;
import com.lotflow.security.CurrentUser;
import com.lotflow.service.NotificationService;
import com.lotflow.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 当前用户站内通知和未读数。 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    @GetMapping
    public CursorPageVO<NotificationVO> list(@RequestParam(required = false) String cursor,
                                             @RequestParam(defaultValue = "20") int limit) {
        return notificationService.list(currentUser.id(), cursor, limit);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("unread", notificationService.unreadCount(currentUser.id()));
    }

    @PostMapping("/{notificationId}/reads")
    public ResponseEntity<Void> markRead(@PathVariable long notificationId) {
        // 已读是通知的一次状态动作，归属校验在 SQL 条件中完成，重复提交保持幂等。
        notificationService.markRead(currentUser.id(), notificationId);
        return ResponseEntity.noContent().build();
    }
}
