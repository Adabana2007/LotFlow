package com.lotflow.dto;

import com.lotflow.entity.enums.DomainEnums.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 人工账号状态调整。 */
public record UserStatusDTO(@NotNull UserStatus status,
                            @Size(max = 1000) String reason) {
}
