package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** 申请商品图片直传票据。 */
public record MediaUploadDTO(
        @NotBlank String contentType,
        @Positive long sizeBytes
) {
}
