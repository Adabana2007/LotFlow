package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 管理员对聊天风险事件作出的可审计人工处置。 */
public record RiskDispositionDTO(@NotNull Action action,
                                 @NotBlank @Size(max = 1000) String reason) {

    public enum Action {
        MARK_FALSE_POSITIVE,
        RECORD_REMINDER,
        RESTRICT_ACCOUNT,
        DISABLE_ACCOUNT
    }
}
