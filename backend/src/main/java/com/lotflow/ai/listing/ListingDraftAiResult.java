package com.lotflow.ai.listing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** AI 发布草稿结构化结果，必须由卖家确认。 */
public record ListingDraftAiResult(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 5000) String description,
        @NotBlank String categoryCode,
        @NotBlank String conditionCode,
        String defects,
        String suggestedStartPrice,
        String suggestedSeckillPrice,
        String suggestedShopPrice,
        List<String> missingInformation
) {
}
