package com.lotflow.service.impl;

import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import com.lotflow.dto.ReviewDecisionDTO;
import com.lotflow.entity.AuditLogDO;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.enums.DomainEnums.ListingStatus;
import com.lotflow.entity.enums.DomainEnums.ReviewDecisionSource;
import com.lotflow.entity.enums.DomainEnums.ReviewDecisionValue;
import com.lotflow.entity.enums.DomainEnums.ReviewStatus;
import com.lotflow.entity.enums.DomainEnums.RiskLevel;
import com.lotflow.mapper.AuditLogMapper;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.mapper.UserMapper;
import com.lotflow.service.OutboxService;
import com.lotflow.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/** 商品人工终审和系统低风险策略。 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ListingMapper listingMapper;
    private final UserMapper userMapper;
    private final AuditLogMapper auditLogMapper;
    private final OutboxService outboxService;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void decide(long reviewerId, long listingId, ReviewDecisionDTO request) {
        // 商品行锁同时保护审核快照和发布状态，两个审核员不能提交相反终审结论。
        ListingDO listing = requirePending(listingId);
        applyDecision(listing, request.decision(), request.reason(), reviewerId,
                ReviewDecisionSource.HUMAN);
    }

    /** AI 只写风险证据并推进人工待审；最终结论仍由人工或显式平台策略产生。 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void applyAiRisk(long listingId, long reviewGeneration, RiskLevel riskLevel,
                            String evidence, boolean policyAutoApprovalEligible) {
        ListingDO listing = listingMapper.selectByIdForUpdate(listingId);
        if (listing == null || listing.getReviewGeneration() == null
                || listing.getReviewGeneration() != reviewGeneration
                || listing.getReviewStatus() == null
                || !pendingReviewStates().contains(listing.getReviewStatus())) {
            // generation 不匹配说明卖家已重新提交，旧模型结果不能覆盖新内容。
            return;
        }
        listing.setRiskLevel(riskLevel);
        listing.setReviewEvidence(evidence);
        listing.setReviewStatus(ReviewStatus.MANUAL_PENDING);

        boolean automaticApproval = !Boolean.TRUE.equals(listing.getManualRequired())
                && riskLevel == RiskLevel.LOW && policyAutoApprovalEligible;
        if (automaticApproval) {
            // 自动通过由平台确定性策略执行，不能表述为 AI 决定通过。
            applyDecision(listing, ReviewDecisionValue.APPROVED,
                    "系统策略：非新卖家、规则无命中、AI风险标签为低风险",
                    null, ReviewDecisionSource.SYSTEM_POLICY);
            return;
        }
        listingMapper.updateById(listing);
        outboxService.append("listing.ai-review-completed", "listing", listingId,
                Map.of("listingId", listingId, "riskLevel", riskLevel.name()));
    }

    private void applyDecision(ListingDO listing, ReviewDecisionValue decision, String reason,
                               Long reviewerId, ReviewDecisionSource source) {
        if (listing.getStatus() != ListingStatus.PENDING_REVIEW
                || listing.getReviewStatus() == null
                || !pendingReviewStates().contains(listing.getReviewStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "商品不在待审核状态");
        }
        String before = listing.getStatus().name();
        listing.setReviewDecision(decision);
        listing.setReviewDecisionSource(source);
        listing.setReviewedBy(reviewerId);
        listing.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));
        applyListingStatus(listing, decision, reason);
        listingMapper.updateById(listing);

        writeAudit(listing, before, decision, reason, reviewerId, source);
        if (decision == ReviewDecisionValue.APPROVED) {
            incrementSellerReviewCount(listing.getSellerId());
            // 审核事务只写事件；向量消费失败由商品索引状态和补偿任务收敛。
            outboxService.append("listing.approved", "listing", listing.getId(),
                    Map.of("listingId", listing.getId(),
                            "reviewGeneration", listing.getReviewGeneration()));
        }
        outboxService.append("review.decided", "listing", listing.getId(),
                Map.of("decision", decision.name(), "source", source.name()));
    }

    private ListingDO requirePending(long listingId) {
        ListingDO listing = listingMapper.selectByIdForUpdate(listingId);
        if (listing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "审核任务不存在");
        }
        if (listing.getStatus() != ListingStatus.PENDING_REVIEW
                || listing.getReviewStatus() == null
                || !pendingReviewStates().contains(listing.getReviewStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "审核任务已完成");
        }
        return listing;
    }

    private java.util.Set<ReviewStatus> pendingReviewStates() {
        return java.util.Set.of(ReviewStatus.PENDING_AI, ReviewStatus.MANUAL_PENDING);
    }

    private void applyListingStatus(ListingDO listing, ReviewDecisionValue decision, String reason) {
        if (decision == ReviewDecisionValue.APPROVED) {
            listing.setStatus(ListingStatus.APPROVED);
            listing.setReviewStatus(listing.getReviewDecisionSource() == ReviewDecisionSource.HUMAN
                    ? ReviewStatus.APPROVED_BY_HUMAN
                    : ReviewStatus.APPROVED_BY_SYSTEM_POLICY);
            return;
        }
        listing.setRejectionReason(reason);
        if (decision == ReviewDecisionValue.CHANGES_REQUIRED) {
            listing.setStatus(ListingStatus.CHANGES_REQUIRED);
            listing.setReviewStatus(ReviewStatus.CHANGES_REQUIRED);
            return;
        }
        listing.setStatus(ListingStatus.REJECTED);
        listing.setReviewStatus(ReviewStatus.REJECTED);
    }

    private void incrementSellerReviewCount(long sellerId) {
        // 完成审核数决定新卖家是否退出强制人工队列，必须用数据库原子递增防止并发审核丢失计数。
        if (userMapper.incrementCompletedReviewCount(sellerId) == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "卖家账号不存在");
        }
    }

    private void writeAudit(ListingDO listing, String before, ReviewDecisionValue decision,
                            String reason, Long reviewerId, ReviewDecisionSource source) {
        AuditLogDO audit = new AuditLogDO();
        audit.setActorId(reviewerId);
        audit.setAction("LISTING_REVIEW_" + decision.name());
        audit.setResourceType("LISTING");
        audit.setResourceId(listing.getId());
        audit.setBeforeState(before);
        audit.setAfterState(listing.getStatus().name());
        audit.setReason(reason);
        auditLogMapper.insert(audit);
    }
}
