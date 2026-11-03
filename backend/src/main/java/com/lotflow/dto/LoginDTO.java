package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 登录请求；phone 字段同时接受用户名或手机号。 */
public record LoginDTO(
        @NotBlank String phone,
        @NotBlank String password,
        @Size(max = 120) String deviceName
) {
}
