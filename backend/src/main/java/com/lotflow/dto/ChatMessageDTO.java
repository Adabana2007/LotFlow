package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 聊天消息正文。 */
public record ChatMessageDTO(@NotBlank @Size(max = 4000) String content) {
}
