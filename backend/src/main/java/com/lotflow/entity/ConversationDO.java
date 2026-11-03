package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lotflow.entity.enums.DomainEnums.ConversationStatus;
import lombok.Getter;
import lombok.Setter;

/** 买卖家会话及其双方已读游标。 */
@Getter
@Setter
@TableName("conversation")
public class ConversationDO extends BaseDO {
    private Long listingId;
    private Long buyerId;
    private Long sellerId;
    private ConversationStatus status;
    private Long lastSequence;
    private Long buyerLastReadSequence;
    private Long sellerLastReadSequence;
}
