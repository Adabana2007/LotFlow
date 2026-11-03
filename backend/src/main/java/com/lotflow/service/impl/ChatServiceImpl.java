package com.lotflow.service.impl;

import com.lotflow.service.AiTaskService;
import com.lotflow.service.ChatService;
import com.lotflow.service.OutboxService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lotflow.common.AfterCommit;
import com.lotflow.common.BusinessException;
import com.lotflow.common.CursorPageVO;
import com.lotflow.common.ResultCode;
import com.lotflow.dto.ChatMessageDTO;
import com.lotflow.entity.ConversationDO;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.MessageDO;
import com.lotflow.entity.OrderDO;
import com.lotflow.entity.UserDO;
import com.lotflow.entity.enums.DomainEnums.ConversationStatus;
import com.lotflow.entity.enums.DomainEnums.OrderStatus;
import com.lotflow.mapper.ConversationMapper;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.mapper.MessageMapper;
import com.lotflow.mapper.OrderMapper;
import com.lotflow.mapper.UserMapper;
import com.lotflow.vo.ConversationVO;
import com.lotflow.vo.MessageVO;
import com.lotflow.websocket.RealtimePublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/** 会话权限、聊天消息和聊天开关。 */
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ListingMapper listingMapper;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final OutboxService outboxService;
    private final RealtimePublisher realtimePublisher;
    private final AiTaskService aiTaskService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ConversationVO create(long buyerId, long listingId) {
        ListingDO listing = listingMapper.selectById(listingId);
        if (listing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        if (listing.getSellerId().equals(buyerId)) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "卖家不能与自己创建会话");
        }
        ConversationDO existing = conversationMapper.selectByBuyerAndListing(buyerId, listingId);
        if (existing != null) {
            // 同一买家对同一商品只保留一个有效会话，重复进入聊天页直接返回已有记录。
            return ConversationVO.from(existing);
        }
        if (!canStartConversation(listing, buyerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该商品当前未开启聊天");
        }
        ConversationDO conversation = new ConversationDO();
        conversation.setListingId(listingId);
        conversation.setBuyerId(buyerId);
        conversation.setSellerId(listing.getSellerId());
        conversation.setStatus(ConversationStatus.OPEN);
        conversation.setLastSequence(0L);
        conversation.setBuyerLastReadSequence(0L);
        conversation.setSellerLastReadSequence(0L);
        conversationMapper.insert(conversation);
        outboxService.append("conversation.created", "conversation", conversation.getId(),
                Map.of("conversationId", conversation.getId()));
        return ConversationVO.from(conversation);
    }

    /** 消息序号在锁定会话行后递增，确保多实例下同会话顺序稳定。 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public MessageVO send(long senderId, long conversationId, ChatMessageDTO request) {
        ConversationDO conversation = conversationMapper.selectByIdForUpdate(conversationId);
        requireParticipant(conversation, senderId);
        // 会话行锁把 last_sequence 递增和消息插入串行化，多实例同时发送也不会产生重复序号。
        long sequence = conversation.getLastSequence() + 1;
        conversation.setLastSequence(sequence);
        conversationMapper.updateById(conversation);

        MessageDO message = new MessageDO();
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setSequenceNo(sequence);
        message.setContent(request.content());
        messageMapper.insert(message);
        outboxService.append("message.created", "conversation", conversationId,
                Map.of("messageId", message.getId(), "sequence", sequence));
        long aiTaskId = aiTaskService.createChatRiskTask(message.getId());
        // 风险分析作为独立事件异步执行；消息已提交后即使 AI 或 MQ 故障也不影响聊天事实。
        outboxService.append("message.risk-requested", "message", message.getId(),
                Map.of("aiTaskId", aiTaskId, "messageId", message.getId()));

        MessageVO vo = MessageVO.from(message);
        // 消息已经落库后才推送；推送失败不能回滚聊天事实，重连会通过 REST 补齐。
        AfterCommit.run(() -> realtimePublisher.publish("/topic/conversations/" + conversationId,
                "MESSAGE_CREATED", Long.toString(conversationId), sequence, vo));
        return vo;
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<MessageVO> messages(long userId, long conversationId,
                                             Long afterSequence, int requestedLimit) {
        ConversationDO conversation = conversationMapper.selectById(conversationId);
        requireParticipant(conversation, userId);
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        List<MessageDO> rows = messageMapper.selectAfter(conversationId, afterSequence, limit);
        Long next = rows.isEmpty() ? null : rows.getLast().getSequenceNo();
        return new CursorPageVO<>(rows.stream().map(MessageVO::from).toList(),
                next == null ? null : next.toString());
    }

    @Transactional(readOnly = true)
    @Override
    public boolean canAccess(long conversationId, long userId) {
        ConversationDO conversation = conversationMapper.selectById(conversationId);
        return conversation != null && (conversation.getBuyerId().equals(userId)
                || conversation.getSellerId().equals(userId));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void markRead(long userId, long conversationId, long sequence) {
        ConversationDO conversation = conversationMapper.selectByIdForUpdate(conversationId);
        requireParticipant(conversation, userId);
        // 已读位置最多推进到会话当前最大序号，客户端伪造超大 sequence 不能越过尚未产生的消息。
        long cursor = Math.max(sequence, currentReadSequence(conversation, userId));
        cursor = Math.min(cursor, conversation.getLastSequence());
        if (conversation.getBuyerId().equals(userId)) {
            conversation.setBuyerLastReadSequence(cursor);
        } else {
            conversation.setSellerLastReadSequence(cursor);
        }
        conversationMapper.updateById(conversation);
    }

    @Transactional(readOnly = true)
    @Override
    public long unread(long userId, long conversationId) {
        ConversationDO conversation = conversationMapper.selectById(conversationId);
        requireParticipant(conversation, userId);
        long read = currentReadSequence(conversation, userId);
        return Math.max(0, conversation.getLastSequence() - read);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateListingChat(long sellerId, long listingId, boolean enabled) {
        ListingDO listing = listingMapper.selectByIdForUpdate(listingId);
        if (listing == null || !listing.getSellerId().equals(sellerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权修改该商品");
        }
        // 商品级开关直接保存在 listing；关闭只阻止新会话，既有履约会话继续可用。
        listing.setChatEnabled(enabled);
        listingMapper.updateById(listing);
    }

    private boolean canStartConversation(ListingDO listing, long buyerId) {
        if (listing.getChatEnabled() != null) {
            return listing.getChatEnabled();
        }
        UserDO seller = userMapper.selectById(listing.getSellerId());
        if (seller != null && Boolean.TRUE.equals(seller.getChatEnabled())) {
            return true;
        }
        Long activeOrder = orderMapper.selectCount(new LambdaQueryWrapper<OrderDO>()
                .eq(OrderDO::getListingId, listing.getId())
                .eq(OrderDO::getBuyerId, buyerId)
                .in(OrderDO::getStatus, OrderStatus.PAID, OrderStatus.SHIPPED,
                        OrderStatus.RECEIVED, OrderStatus.DISPUTED));
        return activeOrder != null && activeOrder > 0;
    }

    private long currentReadSequence(ConversationDO conversation, long userId) {
        Long sequence = conversation.getBuyerId().equals(userId)
                ? conversation.getBuyerLastReadSequence()
                : conversation.getSellerLastReadSequence();
        return sequence == null ? 0 : sequence;
    }

    private void requireParticipant(ConversationDO conversation, long userId) {
        if (conversation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        if (!conversation.getBuyerId().equals(userId) && !conversation.getSellerId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该会话");
        }
    }
}
