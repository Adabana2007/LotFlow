package com.lotflow.vo;

import com.lotflow.entity.SaleChannelDO;

import java.time.OffsetDateTime;

/** 商品渠道历史响应。 */
public record SaleChannelVO(String id, String listingId, String channel, String status,
                            String price, OffsetDateTime startsAt, OffsetDateTime endsAt,
                            String orderId, String decisionStatus, String decisionValue,
                            OffsetDateTime decisionExpiresAt) {
    public static SaleChannelVO from(SaleChannelDO channel) {
        return new SaleChannelVO(channel.getId().toString(), channel.getListingId().toString(),
                channel.getChannel().name(), channel.getStatus().name(),
                channel.getPrice().toPlainString(), channel.getStartsAt(), channel.getEndsAt(),
                channel.getOrderId() == null ? null : channel.getOrderId().toString(),
                channel.getDecisionStatus() == null ? null : channel.getDecisionStatus().name(),
                channel.getDecisionValue() == null ? null : channel.getDecisionValue().name(),
                channel.getDecisionExpiresAt());
    }
}
