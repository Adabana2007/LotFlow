package com.lotflow.service.impl;

import com.lotflow.service.OutboxService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotflow.entity.OutboxEventDO;
import com.lotflow.mapper.OutboxEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 本地事务 Outbox 事件写入。 */
@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventMapper mapper;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @Override
    public void append(String eventType, String aggregateType, long aggregateId, Object payload) {
        // MANDATORY 阻止脱离业务事务单独写事件，保证状态变化和事件同时提交或回滚。
        OutboxEventDO event = new OutboxEventDO();
        event.setEventType(eventType);
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setPayload(toJson(payload));
        event.setStatus("PENDING");
        event.setAvailableAt(OffsetDateTime.now(ZoneOffset.UTC));
        event.setRetryCount(0);
        mapper.insert(event);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("cannot serialize outbox event", exception);
        }
    }
}
