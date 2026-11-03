package com.lotflow.dto;

import jakarta.validation.constraints.NotNull;

/** 商家经营分析对象。 */
public record SellerAnalysisRequestDTO(@NotNull Long listingId) {
}
