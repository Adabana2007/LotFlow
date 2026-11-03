package com.lotflow.ai.seller;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** 商家经营建议。 */
public record SellerAnalysisAiResult(
        @NotBlank String summary,
        List<String> recommendations,
        String suggestedSeckillPrice,
        String suggestedShopPrice
) {
}
