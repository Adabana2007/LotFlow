package com.lotflow.service;

import com.lotflow.common.CursorPageVO;
import com.lotflow.vo.AdminUserVO;
import com.lotflow.vo.AdminReportsVO;
import com.lotflow.vo.AiUsageSummaryVO;
import com.lotflow.vo.AiTaskVO;
import com.lotflow.vo.InventoryDiagnosticVO;
import com.lotflow.vo.OrderVO;
import com.lotflow.vo.ReviewTaskVO;
import com.lotflow.vo.RiskEventVO;
import com.lotflow.vo.WalletTransactionVO;
import com.lotflow.vo.WalletReconciliationVO;

import java.util.List;

/** 运营后台只读查询。 */
public interface AdminQueryService {

    CursorPageVO<ReviewTaskVO> reviewTasks(String cursor, int limit);

    CursorPageVO<OrderVO> orders(String cursor, int limit);

    CursorPageVO<WalletTransactionVO> walletTransactions(String cursor, int limit);

    CursorPageVO<RiskEventVO> riskEvents(String cursor, int limit);

    CursorPageVO<AiTaskVO> aiTasks(String cursor, int limit);

    CursorPageVO<AdminUserVO> users(String cursor, int limit);

    List<InventoryDiagnosticVO> inventoryDiagnostics(int limit);

    AdminReportsVO reports(int days);

    List<WalletReconciliationVO> walletReconciliation(int limit);

    AiUsageSummaryVO aiUsage();
}
