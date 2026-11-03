package com.lotflow.messaging;

import com.lotflow.entity.OutboxEventDO;
import com.lotflow.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Outbox 事件的领取与状态回写，每个动作都是独立短事务。 */
@Component
@RequiredArgsConstructor
public class OutboxRelayStore {

    private final OutboxEventMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public List<OutboxEventDO> claim(int limit) {
        List<OutboxEventDO> events = mapper.selectReady(limit);
        if (events.isEmpty()) {
            return List.of();
        }
        // 行锁只在本事务内有效，提交前必须把可发送时间推到租约之后，否则其他实例会领到同一批事件。
        mapper.lease(events.stream().map(OutboxEventDO::getId).toList());
        return events;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markPublished(long eventId) {
        mapper.markPublished(eventId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFailed(long eventId, String lastError) {
        // 显式 SQL 回写重试次数和下次可发送时间，避免实体乐观锁在并发重试中被覆盖。
        mapper.markFailed(eventId, lastError);
    }
}
