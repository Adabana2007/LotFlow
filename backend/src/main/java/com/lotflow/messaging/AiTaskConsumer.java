package com.lotflow.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotflow.config.RabbitConfig;
import com.lotflow.mapper.ConsumedEventMapper;
import com.lotflow.service.OperationMetricsService;
import com.lotflow.service.impl.AiWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** 从 RabbitMQ 执行可恢复的 AI 审核任务。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskConsumer {

    private static final String CONSUMER_NAME = "ai-task-v1";

    private final ObjectMapper objectMapper;
    private final ConsumedEventMapper consumedEventMapper;
    private final AiWorker worker;
    private final OperationMetricsService metrics;

    @RabbitListener(queues = RabbitConfig.AI_TASK_QUEUE)
    public void consume(@Payload String payload, @Header("eventId") long eventId,
                        @Header("amqp_receivedRoutingKey") String routingKey) {
        Long claimed = consumedEventMapper.tryStart(eventId, CONSUMER_NAME);
        if (claimed == null) {
            return;
        }
        try {
            AiTaskEvent event = deserialize(payload);
            switch (routingKey) {
                case "listing.review-requested" -> worker.review(event.aiTaskId(),
                        event.listingId(), event.reviewGeneration());
                case "message.risk-requested" -> worker.chatRisk(event.aiTaskId(), event.messageId());
                default -> throw new IllegalArgumentException("unsupported AI event: " + routingKey);
            }
            consumedEventMapper.markSucceeded(eventId, CONSUMER_NAME);
            metrics.recordMessageConsumed(CONSUMER_NAME, "success");
        } catch (RuntimeException exception) {
            consumedEventMapper.markFailed(eventId, CONSUMER_NAME,
                    exception.getClass().getSimpleName());
            metrics.recordMessageConsumed(CONSUMER_NAME, "failure");
            log.warn("ai event failed, eventId={}, routingKey={}", eventId, routingKey);
            throw exception;
        }
    }

    private AiTaskEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, AiTaskEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("AI event payload is invalid", exception);
        }
    }
}
