package com.lotflow.vo;

import com.lotflow.entity.ListingDO;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 商品公开视图，金额和 ID 均为字符串。 */
public record ListingVO(
        String id,
        String sellerId,
        String title,
        String description,
        String categoryCode,
        String conditionCode,
        String defects,
        String startPrice,
        String seckillPrice,
        String shopPrice,
        int durationHours,
        OffsetDateTime scheduledStart,
        boolean autoDowngrade,
        String status,
        String rejectionReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version,
        String coverUrl
) {
    public static ListingVO from(ListingDO listing) {
        return from(listing, null);
    }

    public static ListingVO from(ListingDO listing, String coverUrl) {
        return new ListingVO(listing.getId().toString(), listing.getSellerId().toString(),
                listing.getTitle(), listing.getDescription(), listing.getCategoryCode(),
                listing.getConditionCode(), listing.getDefects(), money(listing.getStartPrice()),
                money(listing.getSeckillPrice()), money(listing.getShopPrice()),
                listing.getDurationHours(), listing.getScheduledStart(),
                Boolean.TRUE.equals(listing.getIsAutoDowngrade()), listing.getStatus().name(),
                listing.getRejectionReason(), listing.getGmtCreate(), listing.getGmtModified(),
                listing.getVersion() == null ? 0L : listing.getVersion(), coverUrl);
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
