package com.lotflow.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 个人会话列表中的交易上下文、最后一条消息和未读数。 */
@Getter
@Setter
public class ConversationSummaryVO {
    private String id;
    private String listingId;
    private String listingTitle;
    private String otherPartyId;
    private String otherPartyName;
    private String status;
    private long lastSequence;
    private long unreadCount;
    private String lastMessage;
    private OffsetDateTime lastMessageAt;
}
