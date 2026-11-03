package com.lotflow.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotflow.config.RabbitConfig;
import com.lotflow.entity.AuctionDO;
import com.lotflow.entity.ConversationDO;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.MessageDO;
import com.lotflow.entity.OrderDO;
import com.lotflow.entity.enums.DomainEnums.NotificationType;
import com.lotflow.entity.enums.DomainEnums.OrderStatus;
import com.lotflow.mapper.AuctionMapper;
import com.lotflow.mapper.ConversationMapper;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.mapper.MessageMapper;
import com.lotflow.mapper.OrderMapper;
import com.lotflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** 把交易和聊天事件转换成站内通知。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;
    private final OrderMapper orderMapper;
    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final AuctionMapper auctionMapper;
    private final ListingMapper listingMapper;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitConfig.NOTIFY_QUEUE)
    public void consume(@Payload String payload, @Header("eventId") long eventId,
                        @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        JsonNode event = read(payload);
        switch (routingKey) {
            case "order.created" -> orderCreated(event, eventId);
            case "order.paid" -> orderPaid(event, eventId);
            case "message.created" -> messageCreated(event, eventId);
            case "auction.ended" -> auctionEnded(event, eventId);
            default -> log.debug("notification skipped, routingKey={}", routingKey);
        }
    }

    private void orderCreated(JsonNode event, long eventId) {
        OrderDO order = orderMapper.selectById(event.path("orderId").asLong());
        // 事件可能晚于用户支付，已推进的订单不再发送待支付提醒。
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }
        notificationService.push(order.getBuyerId(), NotificationType.ORDER, "订单待支付",
                "订单 " + order.getOrderNo() + " 需支付 " + order.getAmount().toPlainString()
                        + " 元，请在付款截止时间前完成支付。",
                "ORDER", order.getId(), bizKey("order.created", eventId, order.getBuyerId()));
    }

    private void orderPaid(JsonNode event, long eventId) {
        OrderDO order = orderMapper.selectById(event.path("orderId").asLong());
        if (order == null || order.getStatus() != OrderStatus.PAID) {
            return;
        }
        notificationService.push(order.getSellerId(), NotificationType.ORDER, "买家已付款",
                "订单 " + order.getOrderNo() + " 已付款，请在 72 小时内发货。",
                "ORDER", order.getId(), bizKey("order.paid", eventId, order.getSellerId()));
    }

    private void messageCreated(JsonNode event, long eventId) {
        MessageDO message = messageMapper.selectById(event.path("messageId").asLong());
        if (message == null) {
            return;
        }
        ConversationDO conversation = conversationMapper.selectById(message.getConversationId());
        if (conversation == null) {
            return;
        }
        // 收件人是会话中对端；自己的消息不能触发自己的提醒。
        long recipientId = conversation.getBuyerId().equals(message.getSenderId())
                ? conversation.getSellerId() : conversation.getBuyerId();
        notificationService.push(recipientId, NotificationType.MESSAGE, "收到新消息",
                message.getContent(), "CONVERSATION", conversation.getId(),
                bizKey("message.created", eventId, recipientId));
    }

    private void auctionEnded(JsonNode event, long eventId) {
        AuctionDO auction = auctionMapper.selectById(event.path("auctionId").asLong());
        if (auction == null) {
            return;
        }
        ListingDO listing = listingMapper.selectById(auction.getListingId());
        String title = listing == null ? "拍品" : listing.getTitle();
        if (auction.getHighestBidderId() == null) {
            notificationService.push(auction.getSellerId(), NotificationType.AUCTION, "拍卖流拍",
                    "「" + title + "」没有有效出价，请尽快处理库存。", "AUCTION", auction.getId(),
                    bizKey("auction.ended", eventId, auction.getSellerId()));
            return;
        }
        notificationService.push(auction.getSellerId(), NotificationType.AUCTION, "拍品已成交",
                "「" + title + "」已成交，等待买家付款。", "AUCTION", auction.getId(),
                bizKey("auction.ended", eventId, auction.getSellerId()));
        notificationService.push(auction.getHighestBidderId(), NotificationType.AUCTION, "恭喜中标",
                "「" + title + "」由你拍得，请在 24 小时内完成付款。", "AUCTION", auction.getId(),
                bizKey("auction.ended", eventId, auction.getHighestBidderId()));
    }

    private JsonNode read(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            // 事件体由本服务 Outbox 生成，解析失败说明数据已损坏；抛出后由统一重试和死信保留现场。
            throw new IllegalArgumentException("invalid notification event payload", exception);
        }
    }

    private String bizKey(String eventType, long eventId, long userId) {
        return eventType + ":" + eventId + ":" + userId;
    }
}
