package com.lotflow.vo;

import com.lotflow.entity.ListingDO;

import java.time.OffsetDateTime;

/** 商品审核快照的运营视图，任务 ID 与商品 ID 相同。 */
public record ReviewTaskVO(String id, String listingId, String sellerId, String status,
                           String riskLevel, String evidenceSummary, boolean manualRequired,
                           OffsetDateTime createdAt) {
    public static ReviewTaskVO from(ListingDO listing) {
        return new ReviewTaskVO(listing.getId().toString(), listing.getId().toString(),
                listing.getSellerId().toString(),
                listing.getReviewStatus() == null ? listing.getStatus().name()
                        : listing.getReviewStatus().name(),
                listing.getRiskLevel() == null ? null : listing.getRiskLevel().name(),
                listing.getReviewEvidence(), Boolean.TRUE.equals(listing.getManualRequired()),
                listing.getGmtCreate());
    }
}
