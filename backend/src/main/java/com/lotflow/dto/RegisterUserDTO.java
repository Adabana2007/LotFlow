package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 用户注册请求。 */
public record RegisterUserDTO(
        @Pattern(regexp = "^$|^[A-Za-z0-9_]{3,32}$") String username,
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String phone,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 80) String nickname
) {
}
