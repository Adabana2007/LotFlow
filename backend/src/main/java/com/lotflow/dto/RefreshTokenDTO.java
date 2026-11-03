package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;

/** Refresh Token 轮换请求。 */
public record RefreshTokenDTO(@NotBlank String refreshToken) {
}
