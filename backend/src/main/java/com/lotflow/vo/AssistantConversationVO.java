package com.lotflow.vo;

import com.lotflow.entity.AssistantConversationDO;

import java.time.OffsetDateTime;

/** 助手会话列表项。 */
public record AssistantConversationVO(String id, String title, OffsetDateTime lastMessageAt) {

    public static AssistantConversationVO from(AssistantConversationDO conversation) {
        return new AssistantConversationVO(conversation.getId().toString(), conversation.getTitle(),
                conversation.getLastMessageAt());
    }
}
