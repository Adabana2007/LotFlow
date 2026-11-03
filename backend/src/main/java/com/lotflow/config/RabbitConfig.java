package com.lotflow.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

/** 异步事件 Exchange、队列和死信配置。 */
@Configuration
@EnableRabbit
public class RabbitConfig {

    public static final String EXCHANGE = "lotflow.events";
    public static final String EVENT_QUEUE = "lotflow.events.audit";
    public static final String AI_TASK_QUEUE = "lotflow.ai.tasks";
    public static final String VECTOR_QUEUE = "lotflow.ai.vector";
    public static final String NOTIFY_QUEUE = "lotflow.notify";
    public static final String DEAD_LETTER_EXCHANGE = "lotflow.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "lotflow.events.dead";

    @Bean
    public TopicExchange lotFlowEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue lotFlowEventQueue() {
        // 审计队列持久化并绑定死信 Exchange，消费失败不会无限阻塞主队列。
        return org.springframework.amqp.core.QueueBuilder.durable(EVENT_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding lotFlowEventBinding(Queue lotFlowEventQueue, TopicExchange lotFlowEventsExchange) {
        // 审计队列保留完整事件镜像，用于验证 Outbox 路由和消费链路。
        return BindingBuilder.bind(lotFlowEventQueue).to(lotFlowEventsExchange).with("#");
    }

    @Bean
    public Queue lotFlowAiTaskQueue() {
        return org.springframework.amqp.core.QueueBuilder.durable(AI_TASK_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding lotFlowAiReviewBinding(Queue lotFlowAiTaskQueue,
                                          TopicExchange lotFlowEventsExchange) {
        return BindingBuilder.bind(lotFlowAiTaskQueue).to(lotFlowEventsExchange)
                .with("listing.review-requested");
    }

    @Bean
    public Binding lotFlowChatRiskBinding(Queue lotFlowAiTaskQueue,
                                          TopicExchange lotFlowEventsExchange) {
        return BindingBuilder.bind(lotFlowAiTaskQueue).to(lotFlowEventsExchange)
                .with("message.risk-requested");
    }

    @Bean
    public Queue lotFlowVectorQueue() {
        return org.springframework.amqp.core.QueueBuilder.durable(VECTOR_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding lotFlowVectorBinding(Queue lotFlowVectorQueue,
                                        TopicExchange lotFlowEventsExchange) {
        return BindingBuilder.bind(lotFlowVectorQueue).to(lotFlowEventsExchange)
                .with("listing.approved");
    }

    @Bean
    public Queue lotFlowNotifyQueue() {
        // 通知属于可重放的派生数据，失败进入死信而不是阻塞交易事件的消费。
        return org.springframework.amqp.core.QueueBuilder.durable(NOTIFY_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding lotFlowNotifyOrderCreatedBinding(Queue lotFlowNotifyQueue,
                                                    TopicExchange lotFlowEventsExchange) {
        return BindingBuilder.bind(lotFlowNotifyQueue).to(lotFlowEventsExchange).with("order.created");
    }

    @Bean
    public Binding lotFlowNotifyOrderPaidBinding(Queue lotFlowNotifyQueue,
                                                 TopicExchange lotFlowEventsExchange) {
        return BindingBuilder.bind(lotFlowNotifyQueue).to(lotFlowEventsExchange).with("order.paid");
    }

    @Bean
    public Binding lotFlowNotifyMessageBinding(Queue lotFlowNotifyQueue,
                                               TopicExchange lotFlowEventsExchange) {
        return BindingBuilder.bind(lotFlowNotifyQueue).to(lotFlowEventsExchange)
                .with("message.created");
    }

    @Bean
    public Binding lotFlowNotifyAuctionBinding(Queue lotFlowNotifyQueue,
                                               TopicExchange lotFlowEventsExchange) {
        return BindingBuilder.bind(lotFlowNotifyQueue).to(lotFlowEventsExchange)
                .with("auction.ended");
    }

    @Bean
    public TopicExchange lotFlowDeadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue lotFlowDeadLetterQueue() {
        return org.springframework.amqp.core.QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding lotFlowDeadLetterBinding(Queue lotFlowDeadLetterQueue,
                                            TopicExchange lotFlowDeadLetterExchange) {
        return BindingBuilder.bind(lotFlowDeadLetterQueue).to(lotFlowDeadLetterExchange).with("#");
    }
}
