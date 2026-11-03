package com.lotflow.mapper;

import com.lotflow.vo.AiUsageByModelVO;
import com.lotflow.vo.AiUsageTotalsVO;
import com.lotflow.vo.AdminReportSummaryVO;
import com.lotflow.vo.ChannelInventoryVO;
import com.lotflow.vo.DailyTradeVO;
import com.lotflow.vo.InventoryDiagnosticVO;
import com.lotflow.vo.WalletReconciliationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 运营报表、库存诊断和财务核对的只读查询。 */
@Mapper
public interface AdminInsightMapper {

    List<InventoryDiagnosticVO> selectInventoryDiagnostics(@Param("limit") int limit);

    AdminReportSummaryVO selectReportSummary();

    List<ChannelInventoryVO> selectChannelInventory();

    List<DailyTradeVO> selectTradeTrend(@Param("days") int days);

    List<WalletReconciliationVO> selectWalletReconciliation(@Param("limit") int limit);

    AiUsageTotalsVO selectAiUsageTotals();

    List<AiUsageByModelVO> selectAiUsageByModel();
}
