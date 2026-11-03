package com.lotflow.vo;

import com.lotflow.entity.ConversationDO;

/** 会话摘要。 */
public record ConversationVO(String id, String listingId, String buyerId, String sellerId,
                             String status, long lastSequence) {
    public static ConversationVO from(ConversationDO conversation) {
        return new ConversationVO(conversation.getId().toString(),
                conversation.getListingId().toString(), conversation.getBuyerId().toString(),
                conversation.getSellerId().toString(), conversation.getStatus().name(),
                conversation.getLastSequence());
    }
}
