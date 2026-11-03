package com.lotflow.vo;

/** 运营总览使用的真实交易、渠道与降级汇总。 */
public record AdminReportSummaryVO(
        String completedGmv,
        String escrowBalance,
        long totalOrders,
        long pendingPaymentOrders,
        long disputedOrders,
        long activeAuctions,
        long activeSeckills,
        long activeShops,
        long unsoldAuctions,
        String downgradeRate
) {
}
