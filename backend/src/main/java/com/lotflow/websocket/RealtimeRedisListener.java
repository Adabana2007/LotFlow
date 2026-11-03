package com.lotflow.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** 实时事件本地订阅和推送。 */
@Configuration
@ConditionalOnProperty(name = "lotflow.realtime.enabled", havingValue = "true", matchIfMissing = true)
public class RealtimeRedisListener {

    @Bean
    public RedisMessageListenerContainer realtimeListenerContainer(
            RedisConnectionFactory connectionFactory, ObjectMapper objectMapper,
            SimpMessagingTemplate messagingTemplate) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListener listener = (Message message, byte[] pattern) -> {
            try {
                // 每个实例只消费 Redis 广播并转发给本地 STOMP 连接，避免依赖单机 Session 做跨实例同步。
                RealtimePublisher.RedisEnvelope envelope = objectMapper.readValue(message.getBody(),
                        RealtimePublisher.RedisEnvelope.class);
                messagingTemplate.convertAndSend(envelope.destination(), envelope.envelope());
            } catch (Exception exception) {
                // 单条实时消息损坏不能触发全局监听器退出；记录异常后由下一次业务事件或 REST 拉取补偿。
                throw new IllegalArgumentException("invalid realtime message", exception);
            }
        };
        MessageListenerAdapter adapter = new MessageListenerAdapter(listener);
        container.addMessageListener(adapter, new ChannelTopic(RealtimePublisher.CHANNEL));
        return container;
    }
}
