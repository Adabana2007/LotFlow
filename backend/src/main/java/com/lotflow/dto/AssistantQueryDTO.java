package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 平台助手问题。 */
public record AssistantQueryDTO(@NotBlank @Size(max = 2000) String question) {
}
