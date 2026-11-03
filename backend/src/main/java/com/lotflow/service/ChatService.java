package com.lotflow.service;

import com.lotflow.common.CursorPageVO;
import com.lotflow.dto.ChatMessageDTO;
import com.lotflow.vo.ConversationVO;
import com.lotflow.vo.MessageVO;

/** 会话权限、聊天消息和聊天开关。 */
public interface ChatService {

    ConversationVO create(long buyerId, long listingId);

    MessageVO send(long senderId, long conversationId, ChatMessageDTO request);

    CursorPageVO<MessageVO> messages(long userId, long conversationId,
                                     Long afterSequence, int requestedLimit);

    boolean canAccess(long conversationId, long userId);

    void markRead(long userId, long conversationId, long sequence);

    long unread(long userId, long conversationId);

    void updateListingChat(long sellerId, long listingId, boolean enabled);
}
