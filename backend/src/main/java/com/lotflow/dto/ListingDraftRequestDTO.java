package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** AI 发布草稿请求。 */
public record ListingDraftRequestDTO(
        @NotBlank @Size(max = 3000) String sellerDescription,
        List<String> imageKeys
) {
}
