package com.lotflow.vo;

import java.util.List;

/** AI 用量、延迟和成本汇总；单价未配置时成本保持为空。 */
public record AiUsageSummaryVO(
        AiUsageTotalsVO totals,
        boolean costConfigured,
        String estimatedCost,
        List<AiUsageByModelVO> models
) {
}
