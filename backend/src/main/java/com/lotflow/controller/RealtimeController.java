package com.lotflow.controller;

import com.lotflow.security.CurrentUser;
import com.lotflow.service.RealtimeTicketService;
import com.lotflow.vo.RealtimeTicketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** WebSocket 一次性连接票据接口。 */
@RestController
@RequestMapping("/api/v1/realtime")
@RequiredArgsConstructor
public class RealtimeController {

    private final RealtimeTicketService ticketService;
    private final CurrentUser currentUser;

    @PostMapping("/tickets")
    public RealtimeTicketVO createTicket() {
        // 浏览器不把 Access Token 放进 WebSocket URL；REST 只返回六十秒内可原子消费的一次性票据。
        return ticketService.issue(currentUser.id());
    }
}
