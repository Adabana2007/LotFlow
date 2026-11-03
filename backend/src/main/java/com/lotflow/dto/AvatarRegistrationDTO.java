package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** 头像文件完成直传后的登记请求。 */
public record AvatarRegistrationDTO(
        @NotBlank String objectKey,
        @NotBlank String contentType,
        @Positive long sizeBytes
) {
}
