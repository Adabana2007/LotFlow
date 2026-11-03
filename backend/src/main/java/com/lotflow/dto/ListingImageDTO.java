package com.lotflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** 图片元数据登记请求。 */
public record ListingImageDTO(
        @NotBlank String objectKey,
        @NotBlank String contentType,
        @Positive long sizeBytes,
        @Min(0) int sortOrder
) {
}
