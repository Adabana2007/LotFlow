package com.lotflow.messaging;

import com.lotflow.config.RabbitConfig;
import com.lotflow.entity.OutboxEventDO;
import com.lotflow.service.OperationMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Outbox 中继：领取和回写各自短事务，broker 交互在事务外完成。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 50;
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final OutboxRelayStore store;
    private final RabbitTemplate rabbitTemplate;
    private final OperationMetricsService metrics;
    @Value("${lotflow.outbox.relay-enabled:true}")
    private final boolean enabled;

    @Scheduled(fixedDelayString = "${lotflow.outbox.poll-delay:2000}")
    public void relay() {
        if (!enabled) {
            return;
        }
        List<OutboxEventDO> events = store.claim(BATCH_SIZE);
        for (OutboxEventDO event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEventDO event) {
        CorrelationData correlation = new CorrelationData(event.getId().toString());
        try {
            // confirm ack 和 mandatory return 是不同可靠性信号，必须同时通过才算发布成功。
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, event.getEventType(),
                    event.getPayload(), message -> {
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setHeader("eventId", event.getId());
                        return message;
                    }, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!confirm.ack()) {
                throw new IllegalStateException("broker nack: " + confirm.reason());
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("message returned: "
                        + correlation.getReturned().getReplyText());
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                // 中断标志必须保留，避免停机或线程池回收时继续处理后续事件。
                Thread.currentThread().interrupt();
            }
            metrics.recordOutboxPublish("failure");
            store.markFailed(event.getId(), exception.getClass().getSimpleName());
            log.warn("outbox publish failed, eventId={}", event.getId());
            return;
        }
        store.markPublished(event.getId());
        metrics.recordOutboxPublish("success");
    }
}
