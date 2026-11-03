package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 平台助手会话元数据。 */
@Getter
@Setter
@TableName("assistant_conversation")
public class AssistantConversationDO extends BaseDO {
    private Long userId;
    private String title;
    private OffsetDateTime lastMessageAt;
}
