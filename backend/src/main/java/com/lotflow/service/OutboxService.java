package com.lotflow.service;

/** 本地事务 Outbox 事件写入。 */
public interface OutboxService {

    void append(String eventType, String aggregateType, long aggregateId, Object payload);
}
