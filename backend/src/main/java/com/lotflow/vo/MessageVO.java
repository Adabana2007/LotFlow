package com.lotflow.vo;

import com.lotflow.entity.MessageDO;

import java.time.OffsetDateTime;

/** 聊天消息响应。 */
public record MessageVO(String id, String conversationId, String senderId, long sequence,
                        String content, OffsetDateTime occurredAt) {
    public static MessageVO from(MessageDO message) {
        return new MessageVO(message.getId().toString(), message.getConversationId().toString(),
                message.getSenderId().toString(), message.getSequenceNo(), message.getContent(),
                message.getGmtCreate());
    }
}
