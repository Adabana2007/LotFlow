package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新建助手会话，标题缺省时由第一条问题生成。 */
public record CreateAssistantConversationDTO(@NotBlank @Size(max = 120) String title) {
}
