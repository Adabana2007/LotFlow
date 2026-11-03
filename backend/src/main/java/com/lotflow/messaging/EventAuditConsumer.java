package com.lotflow.messaging;

import com.lotflow.config.RabbitConfig;
import com.lotflow.mapper.ConsumedEventMapper;
import com.lotflow.service.OperationMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** 审计消费者通过唯一键幂等，证明事件至少被一次实际路由和消费。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventAuditConsumer {

    private static final String CONSUMER_NAME = "event-audit-v1";

    private final ConsumedEventMapper consumedEventMapper;
    private final OperationMetricsService metrics;

    @RabbitListener(queues = RabbitConfig.EVENT_QUEUE)
    public void consume(@Payload String payload, @Header("eventId") long eventId) {
        try {
            // eventId + consumer 唯一键保证重复投递只审计一次；异常继续抛出以触发 RabbitMQ 重试或死信。
            Long claimed = consumedEventMapper.tryStart(eventId, CONSUMER_NAME);
            if (claimed == null) {
                return;
            }
            metrics.recordMessageConsumed(CONSUMER_NAME, "success");
            consumedEventMapper.markSucceeded(eventId, CONSUMER_NAME);
            log.debug("event consumed, eventId={}, payloadLength={}", eventId, payload.length());
        } catch (RuntimeException exception) {
            metrics.recordMessageConsumed(CONSUMER_NAME, "failure");
            consumedEventMapper.markFailed(eventId, CONSUMER_NAME,
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }
}
