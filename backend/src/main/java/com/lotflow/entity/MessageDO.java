package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lotflow.entity.enums.DomainEnums.RiskLevel;
import lombok.Getter;
import lombok.Setter;

/** 聊天消息。 */
@Getter
@Setter
@TableName("message")
public class MessageDO extends BaseDO {
    private Long conversationId;
    private Long senderId;
    private Long sequenceNo;
    private String content;
    private RiskLevel riskLevel;
    private String riskCategories;
    private String riskEvidence;
    private Boolean requiresHumanReview;
}
