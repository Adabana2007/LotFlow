package com.lotflow.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotflow.ai.search.VectorIndexService;
import com.lotflow.config.RabbitConfig;
import com.lotflow.mapper.ConsumedEventMapper;
import com.lotflow.service.OperationMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** 审核通过后通过 MQ 建立商品向量索引。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorIndexConsumer {

    private static final String CONSUMER_NAME = "listing-vector-v1";

    private final ObjectMapper objectMapper;
    private final ConsumedEventMapper consumedEventMapper;
    private final VectorIndexService vectorIndexService;
    private final OperationMetricsService metrics;

    @RabbitListener(queues = RabbitConfig.VECTOR_QUEUE)
    public void consume(@Payload String payload, @Header("eventId") long eventId) {
        Long claimed = consumedEventMapper.tryStart(eventId, CONSUMER_NAME);
        if (claimed == null) {
            return;
        }
        try {
            JsonNode event = objectMapper.readTree(payload);
            vectorIndexService.index(event.path("listingId").asLong());
            consumedEventMapper.markSucceeded(eventId, CONSUMER_NAME);
            metrics.recordMessageConsumed(CONSUMER_NAME, "success");
        } catch (RuntimeException | JsonProcessingException exception) {
            consumedEventMapper.markFailed(eventId, CONSUMER_NAME,
                    exception.getClass().getSimpleName());
            metrics.recordMessageConsumed(CONSUMER_NAME, "failure");
            log.warn("vector event failed, eventId={}", eventId);
            throw new IllegalStateException("vector index event failed", exception);
        }
    }
}
