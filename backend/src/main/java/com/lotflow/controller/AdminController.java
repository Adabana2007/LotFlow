package com.lotflow.controller;

import com.lotflow.ai.assistant.rag.KnowledgeIndexService;
import com.lotflow.dto.CreditAdjustmentDTO;
import com.lotflow.dto.DisputeResolutionDTO;
import com.lotflow.dto.RiskDispositionDTO;
import com.lotflow.dto.UserStatusDTO;
import com.lotflow.security.CurrentUser;
import com.lotflow.service.AdminService;
import com.lotflow.service.UserService;
import com.lotflow.service.AdminQueryService;
import com.lotflow.service.ListingService;
import com.lotflow.service.ReviewService;
import com.lotflow.service.PersonalWorkspaceService;
import com.lotflow.common.CursorPageVO;
import com.lotflow.dto.ReviewDecisionDTO;
import com.lotflow.vo.AdminListingDetailVO;
import com.lotflow.vo.CreditVO;
import com.lotflow.vo.AdminUserVO;
import com.lotflow.vo.AiTaskVO;
import com.lotflow.vo.AiUsageSummaryVO;
import com.lotflow.vo.AdminReportsVO;
import com.lotflow.vo.DisputeDetailVO;
import com.lotflow.vo.DisputeVO;
import com.lotflow.vo.InventoryDiagnosticVO;
import com.lotflow.vo.KnowledgeIndexVO;
import com.lotflow.vo.OrderVO;
import com.lotflow.vo.ReviewTaskVO;
import com.lotflow.vo.RiskEventVO;
import com.lotflow.vo.WalletTransactionVO;
import com.lotflow.vo.WalletReconciliationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/** 运营后台人工处置接口。 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final CurrentUser currentUser;
    private final UserService userService;
    private final AdminQueryService adminQueryService;
    private final ReviewService reviewService;
    private final ListingService listingService;
    private final KnowledgeIndexService knowledgeIndexService;
    private final PersonalWorkspaceService personalWorkspaceService;

    @GetMapping("/disputes")
    public CursorPageVO<DisputeVO> disputes(@RequestParam(required = false) String cursor,
                                            @RequestParam(defaultValue = "20") int limit) {
        return personalWorkspaceService.disputes(currentUser.id(), true, cursor, limit);
    }

    @GetMapping("/disputes/{orderId}")
    public DisputeDetailVO dispute(@PathVariable long orderId) {
        return personalWorkspaceService.dispute(currentUser.id(), true, orderId);
    }

    @PostMapping("/disputes/{disputeId}/decisions")
    public ResponseEntity<Void> resolveDispute(@PathVariable long disputeId,
                                               @Valid @RequestBody DisputeResolutionDTO request) {
        // 争议决定会立即结算或退款并可能扣卖家信用，因此只暴露给 ADMIN 并要求填写处理理由。
        adminService.resolveDispute(currentUser.id(), disputeId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable long userId,
                                             @Valid @RequestBody UserStatusDTO request) {
        // 封禁或恢复账号属于人工风控动作，必须记录操作管理员、原因和前后状态。
        adminService.updateUserStatus(currentUser.id(), userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/credit-adjustments")
    public ResponseEntity<Void> adjustCredit(@PathVariable long userId,
                                             @Valid @RequestBody CreditAdjustmentDTO request) {
        // 信用调整会影响出价和发布权限，必须写信用流水和后台审计，不允许 AI 直接调用。
        adminService.adjustCredit(currentUser.id(), userId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{userId}/credit")
    public CreditVO credit(@PathVariable long userId) {
        return userService.getCredit(userId);
    }

    @GetMapping("/review-tasks")
    public CursorPageVO<ReviewTaskVO> reviewTasks(@RequestParam(required = false) String cursor,
                                                  @RequestParam(defaultValue = "20") int limit) {
        return adminQueryService.reviewTasks(cursor, limit);
    }

    @GetMapping("/listings/{listingId}")
    public AdminListingDetailVO reviewDetail(@PathVariable long listingId) {
        // 待审商品不在公开状态白名单内，人工审核必须走后台专用详情才能看到描述和实拍图。
        return listingService.getForReview(listingId);
    }

    @PostMapping("/review-tasks/{taskId}/decisions")
    public ResponseEntity<Void> reviewDecision(@PathVariable long taskId,
                                               @Valid @RequestBody ReviewDecisionDTO request) {
        reviewService.decide(currentUser.id(), taskId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/orders")
    public CursorPageVO<OrderVO> orders(@RequestParam(required = false) String cursor,
                                        @RequestParam(defaultValue = "20") int limit) {
        return adminQueryService.orders(cursor, limit);
    }

    @GetMapping("/wallet-transactions")
    public CursorPageVO<WalletTransactionVO> walletTransactions(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        // 资金流水查询只读且按游标翻页，管理员可审计但不能通过该接口修改余额。
        return adminQueryService.walletTransactions(cursor, limit);
    }

    @GetMapping("/risk-events")
    public CursorPageVO<RiskEventVO> riskEvents(@RequestParam(required = false) String cursor,
                                                @RequestParam(defaultValue = "20") int limit) {
        return adminQueryService.riskEvents(cursor, limit);
    }

    @PostMapping("/risk-events/{messageId}/dispositions")
    public ResponseEntity<Void> disposeRisk(@PathVariable long messageId,
                                            @Valid @RequestBody RiskDispositionDTO request) {
        adminService.disposeRiskEvent(currentUser.id(), messageId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ai-call-logs")
    public CursorPageVO<AiTaskVO> aiTasks(@RequestParam(required = false) String cursor,
                                          @RequestParam(defaultValue = "20") int limit) {
        // AI 调用日志用于追踪模型、Prompt 版本、Token 和失败原因，不返回完整聊天隐私正文。
        return adminQueryService.aiTasks(cursor, limit);
    }

    @GetMapping("/ai/usage")
    public AiUsageSummaryVO aiUsage() {
        return adminQueryService.aiUsage();
    }

    @GetMapping("/ai/knowledge-index")
    public KnowledgeIndexVO knowledgeIndex() {
        return knowledgeIndexService.status();
    }

    @PostMapping("/ai/knowledge-index/rebuilds")
    public ResponseEntity<Void> rebuildKnowledgeIndex() {
        // 规则文件版本变化后由管理员显式重建；索引失败不影响拍卖、订单和资金主链路。
        knowledgeIndexService.rebuildAsync(true);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/inventory-diagnostics")
    public List<InventoryDiagnosticVO> inventoryDiagnostics(
            @RequestParam(defaultValue = "50") int limit) {
        return adminQueryService.inventoryDiagnostics(limit);
    }

    @PostMapping("/inventory-diagnostics/{orderId}/releases")
    public ResponseEntity<Void> retryInventoryRelease(@PathVariable long orderId) {
        adminService.retryInventoryRelease(currentUser.id(), orderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reports")
    public AdminReportsVO reports(@RequestParam(defaultValue = "30") int days) {
        return adminQueryService.reports(days);
    }

    @GetMapping("/finance/reconciliation")
    public List<WalletReconciliationVO> walletReconciliation(
            @RequestParam(defaultValue = "50") int limit) {
        return adminQueryService.walletReconciliation(limit);
    }

    @GetMapping("/users")
    public CursorPageVO<AdminUserVO> users(@RequestParam(required = false) String cursor,
                                           @RequestParam(defaultValue = "20") int limit) {
        return adminQueryService.users(cursor, limit);
    }
}
