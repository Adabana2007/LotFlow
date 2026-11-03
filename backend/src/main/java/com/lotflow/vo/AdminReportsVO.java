package com.lotflow.vo;

import java.util.List;

/** 运营总览、渠道库存和最近交易趋势的组合报表。 */
public record AdminReportsVO(
        AdminReportSummaryVO summary,
        List<ChannelInventoryVO> channels,
        List<DailyTradeVO> trend
) {
}
