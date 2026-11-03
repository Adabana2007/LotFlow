package com.lotflow.service.impl;

import com.lotflow.ai.AiCostProperties;
import com.lotflow.service.AdminQueryService;

import com.lotflow.common.Cursor;
import com.lotflow.common.CursorPageVO;
import com.lotflow.mapper.AiTaskMapper;
import com.lotflow.mapper.AdminInsightMapper;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.mapper.MessageMapper;
import com.lotflow.mapper.OrderMapper;
import com.lotflow.mapper.UserMapper;
import com.lotflow.mapper.WalletTransactionMapper;
import com.lotflow.vo.AdminUserVO;
import com.lotflow.vo.AdminReportsVO;
import com.lotflow.vo.AiUsageByModelVO;
import com.lotflow.vo.AiUsageSummaryVO;
import com.lotflow.vo.AiUsageTotalsVO;
import com.lotflow.vo.AiTaskVO;
import com.lotflow.vo.InventoryDiagnosticVO;
import com.lotflow.vo.OrderVO;
import com.lotflow.vo.ReviewTaskVO;
import com.lotflow.vo.RiskEventVO;
import com.lotflow.vo.WalletTransactionVO;
import com.lotflow.vo.WalletReconciliationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Function;

/** 运营后台只读查询。 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminQueryServiceImpl implements AdminQueryService {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);
    private static final int COST_SCALE = 6;

    private final AdminInsightMapper adminInsightMapper;
    private final ListingMapper listingMapper;
    private final OrderMapper orderMapper;
    private final WalletTransactionMapper walletTransactionMapper;
    private final MessageMapper messageMapper;
    private final AiTaskMapper aiTaskMapper;
    private final UserMapper userMapper;
    private final AiCostProperties aiCostProperties;

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<ReviewTaskVO> reviewTasks(String cursor, int limit) {
        // 审核任务列表按 ID 游标翻页，多查一条只用于判断 hasMore，避免后台统计 COUNT 拖慢主查询。
        var rows = listingMapper.selectReviewPage(Cursor.parse(cursor), bounded(limit) + 1);
        return page(rows, limit, ReviewTaskVO::from, item -> Long.toString(item.getId()));
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<OrderVO> orders(String cursor, int limit) {
        // 订单审计列表只读且按游标稳定倒序，按最新订单优先展示运营需要处理的记录。
        var rows = orderMapper.selectAdminPage(Cursor.parse(cursor), bounded(limit) + 1);
        return page(rows, limit, OrderVO::from, item -> Long.toString(item.getId()));
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<WalletTransactionVO> walletTransactions(String cursor, int limit) {
        // 资金流水后台查询跨越账户，只允许读取，资金变更必须走 WalletService 原子命令。
        var rows = walletTransactionMapper.selectAdminPage(Cursor.parse(cursor), bounded(limit) + 1);
        return page(rows, limit, WalletTransactionVO::from, item -> Long.toString(item.getId()));
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<RiskEventVO> riskEvents(String cursor, int limit) {
        // 风险事件来自聊天 AI 初筛，供运营复核，查询本身不会自动处罚或封号。
        var rows = messageMapper.selectRiskPage(Cursor.parse(cursor), bounded(limit) + 1);
        return page(rows, limit, RiskEventVO::from, item -> Long.toString(item.getId()));
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<AiTaskVO> aiTasks(String cursor, int limit) {
        // AI 调用日志保留模型、Prompt、Token 和错误状态，敏感正文不通过该 VO 暴露。
        var rows = aiTaskMapper.selectPage(Cursor.parse(cursor), bounded(limit) + 1);
        return page(rows, limit, AiTaskVO::from, item -> Long.toString(item.getId()));
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<AdminUserVO> users(String cursor, int limit) {
        // 用户列表只提供运营查看，状态和信用变更必须走人工处置接口并写审计。
        var rows = userMapper.selectPage(Cursor.parse(cursor), bounded(limit) + 1);
        return page(rows, limit, AdminUserVO::from, item -> Long.toString(item.getId()));
    }

    @Transactional(readOnly = true)
    @Override
    public List<InventoryDiagnosticVO> inventoryDiagnostics(int limit) {
        // 诊断只列出已过期的秒杀预占或顺延资格，实际释放仍调用订单状态机而不是直接改表。
        return adminInsightMapper.selectInventoryDiagnostics(bounded(limit));
    }

    @Transactional(readOnly = true)
    @Override
    public AdminReportsVO reports(int days) {
        int boundedDays = Math.max(7, Math.min(days, 90));
        // 汇总、渠道和每日趋势读取同一事实表，页面不会用静态曲线补足无交易日期。
        return new AdminReportsVO(adminInsightMapper.selectReportSummary(),
                adminInsightMapper.selectChannelInventory(),
                adminInsightMapper.selectTradeTrend(boundedDays));
    }

    @Transactional(readOnly = true)
    @Override
    public List<WalletReconciliationVO> walletReconciliation(int limit) {
        // 核对余额与最后一笔流水快照，只暴露账户标识和金额，不返回资金备注或隐私。
        return adminInsightMapper.selectWalletReconciliation(bounded(limit));
    }

    @Transactional(readOnly = true)
    @Override
    public AiUsageSummaryVO aiUsage() {
        AiUsageTotalsVO totals = adminInsightMapper.selectAiUsageTotals();
        List<AiUsageByModelVO> rawModels = adminInsightMapper.selectAiUsageByModel();
        List<AiUsageByModelVO> models = rawModels.stream().map(row -> {
            BigDecimal cost = estimateCost(row.modelName(), row.promptTokens(), row.completionTokens());
            return new AiUsageByModelVO(row.modelName(), row.calls(), row.promptTokens(),
                    row.completionTokens(), row.averageLatencyMs(), money(cost));
        }).toList();
        boolean allCostsConfigured = !models.isEmpty()
                && models.stream().allMatch(row -> row.estimatedCost() != null);
        BigDecimal totalCost = allCostsConfigured
                ? models.stream().map(row -> new BigDecimal(row.estimatedCost()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;
        return new AiUsageSummaryVO(totals, allCostsConfigured, money(totalCost), models);
    }

    private BigDecimal estimateCost(String modelName, long promptTokens, long completionTokens) {
        AiCostProperties.ModelRate rate = aiCostProperties.rateFor(modelName);
        if (rate == null || !rate.isConfigured()) {
            return null;
        }
        // Token 单价按每百万 Token 配置；没有单价时不返回零成本，避免把未知费用显示成免费。
        return rate.inputPerMillion()
                .multiply(BigDecimal.valueOf(promptTokens))
                .add(rate.outputPerMillion().multiply(BigDecimal.valueOf(completionTokens)))
                .divide(MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }

    private String money(BigDecimal value) {
        return value == null ? null
                : value.setScale(COST_SCALE, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private int bounded(int limit) {
        // 后台查询同样限制单页最多 50 条，避免运营页面意外拉取全表。
        return Math.max(1, Math.min(limit, 50));
    }

    private <D, V> CursorPageVO<V> page(List<D> rows, int requestedLimit,
                                        Function<D, V> mapper, Function<D, String> cursorOf) {
        int limit = bounded(requestedLimit);
        boolean hasMore = rows.size() > limit;
        List<D> selected = hasMore ? rows.subList(0, limit) : rows;
        // 下一页游标取本页最后一条记录 ID，保证后台并发新增数据时游标分页不重复、不漏项。
        String next = hasMore ? cursorOf.apply(selected.getLast()) : null;
        return new CursorPageVO<>(selected.stream().map(mapper).toList(), next);
    }
}
