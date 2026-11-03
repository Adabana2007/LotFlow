package com.lotflow.vo;

/** 拍卖、秒杀和商城渠道的库存状态汇总。 */
public record ChannelInventoryVO(
        String channel,
        long activeCount,
        long soldCount,
        long unsoldCount,
        long scheduledCount
) {
}
