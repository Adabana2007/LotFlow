package com.lotflow.dto;

import jakarta.validation.constraints.NotNull;

/** 从商品发起会话。 */
public record ConversationDTO(@NotNull Long listingId) {
}
