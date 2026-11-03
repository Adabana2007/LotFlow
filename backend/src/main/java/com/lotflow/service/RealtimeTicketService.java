package com.lotflow.service;

import com.lotflow.vo.RealtimeTicketVO;

/** WebSocket 一次性连接票据。 */
public interface RealtimeTicketService {

    RealtimeTicketVO issue(long userId);

    Long consume(String ticket);
}
