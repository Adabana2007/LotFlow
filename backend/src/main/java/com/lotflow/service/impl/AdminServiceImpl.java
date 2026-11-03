package com.lotflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import com.lotflow.dto.CreditAdjustmentDTO;
import com.lotflow.dto.DisputeResolutionDTO;
import com.lotflow.dto.RiskDispositionDTO;
import com.lotflow.dto.UserStatusDTO;
import com.lotflow.entity.AuditLogDO;
import com.lotflow.entity.DepositHoldDO;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.MessageDO;
import com.lotflow.entity.OrderDO;
import com.lotflow.entity.UserDO;
import com.lotflow.entity.enums.DomainEnums.DepositStatus;
import com.lotflow.entity.enums.DomainEnums.DepositType;
import com.lotflow.entity.enums.DomainEnums.DisputeResolutionValue;
import com.lotflow.entity.enums.DomainEnums.DisputeStatus;
import com.lotflow.entity.enums.DomainEnums.ListingStatus;
import com.lotflow.entity.enums.DomainEnums.OrderStatus;
import com.lotflow.entity.enums.DomainEnums.UserStatus;
import com.lotflow.mapper.AuditLogMapper;
import com.lotflow.mapper.DatabaseTimeMapper;
import com.lotflow.mapper.DepositHoldMapper;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.mapper.MessageMapper;
import com.lotflow.mapper.OrderMapper;
import com.lotflow.mapper.UserMapper;
import com.lotflow.service.AdminService;
import com.lotflow.service.OrderService;
import com.lotflow.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;

/** 运营后台人工处置和统一审计。 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminServiceImpl implements AdminService {

    private static final Set<String> RISK_DISPOSITION_ACTIONS = Set.of(
            "CHAT_RISK_FALSE_POSITIVE", "CHAT_RISK_REMINDER_RECORDED",
            "CHAT_RISK_ACCOUNT_RESTRICTED", "CHAT_RISK_ACCOUNT_DISABLED");

    private final OrderMapper orderMapper;
    private final ListingMapper listingMapper;
    private final DepositHoldMapper depositMapper;
    private final UserMapper userMapper;
    private final AuditLogMapper auditLogMapper;
    private final DatabaseTimeMapper timeMapper;
    private final WalletService walletService;
    private final MessageMapper messageMapper;
    private final OrderService orderService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void resolveDispute(long adminId, long disputeId, DisputeResolutionDTO request) {
        // disputeId 复用订单 ID；争议证据和结果都保存在订单事实中，避免额外关联表。
        OrderDO order = orderMapper.selectByIdForUpdate(disputeId);
        if (order == null || order.getDisputeStatus() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "争议不存在");
        }
        if (order.getDisputeStatus() == DisputeStatus.RESOLVED) {
            throw new BusinessException(ResultCode.CONFLICT, "争议已处理");
        }
        if (order.getStatus() != OrderStatus.DISPUTED) {
            throw new BusinessException(ResultCode.CONFLICT, "订单不在争议状态");
        }

        boolean buyerWins = request.outcome() == DisputeResolutionValue.BUYER_WINS;
        long beneficiary = buyerWins ? order.getBuyerId() : order.getSellerId();
        walletService.transferEscrowToAvailable(beneficiary, order.getEscrowAmount(),
                "dispute:" + disputeId + ":escrow");
        resolveSellerDeposit(disputeId, order, buyerWins);
        if (buyerWins) {
            adjustCredit(adminId, order.getSellerId(), -20, "DISPUTE_BUYER_WINS",
                    "DISPUTE", disputeId);
        }

        OffsetDateTime now = timeMapper.now();
        order.setDisputeStatus(DisputeStatus.RESOLVED);
        order.setDisputeResolution(request.resolution());
        order.setDisputeResolvedBy(adminId);
        order.setDisputeResolvedAt(now);
        order.setStatus(buyerWins ? OrderStatus.CANCELLED : OrderStatus.COMPLETED);
        orderMapper.updateById(order);
        ListingDO listing = listingMapper.selectByIdForUpdate(order.getListingId());
        if (listing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单对应商品不存在");
        }
        // 买家胜诉时担保款已退回，商品不能标记为完成；先终止在售事实，卖家核对退货后再决定是否重新发布。
        listing.setStatus(buyerWins ? ListingStatus.CANCELLED : ListingStatus.COMPLETED);
        listingMapper.updateById(listing);
        writeAudit(adminId, order.getSellerId(), "DISPUTE_RESOLVED", "ORDER", disputeId,
                DisputeStatus.OPEN.name(), request.outcome().name(), null, request.resolution());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateUserStatus(long adminId, long userId, UserStatusDTO request) {
        // 锁定用户行，避免运营处置与账号资料更新互相覆盖版本和状态。
        UserDO user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        String before = user.getStatus().name();
        user.setStatus(request.status());
        // ActiveUserFilter 在下一次请求读取持久化状态，因此禁用立即影响后续鉴权。
        userMapper.updateById(user);
        writeAudit(adminId, userId, "USER_STATUS_CHANGED", "USER", userId, before,
                request.status().name(), null,
                request.reason() == null ? "manual status change" : request.reason());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void adjustCredit(long adminId, long userId, CreditAdjustmentDTO request) {
        UserDO user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        int before = user.getCreditScore();
        int after = Math.max(0, Math.min(120, before + request.changeValue()));
        userMapper.adjustCredit(userId, request.changeValue());
        // 余额边界会在 SQL 中再次收敛；后台审计同时保存实际前后值和管理员填写的调整原因。
        writeAudit(adminId, userId, "CREDIT_ADJUSTED", "USER", userId,
                Integer.toString(before), Integer.toString(after),
                request.changeValue(), request.reason());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void retryInventoryRelease(long adminId, long orderId) {
        OrderDO before = orderMapper.selectById(orderId);
        if (before == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        // 后台按钮只重放既有超时命令；库存、渠道和钱包变化仍由 OrderService 的统一状态机完成。
        orderService.processPendingPayment(orderId);
        OrderDO after = orderMapper.selectById(orderId);
        writeAudit(adminId, before.getBuyerId(), "INVENTORY_RELEASE_RETRIED", "ORDER", orderId,
                before.getStatus().name(), after == null ? null : after.getStatus().name(),
                null, "manual timeout compensation retry");
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void disposeRiskEvent(long adminId, long messageId, RiskDispositionDTO request) {
        MessageDO message = messageMapper.selectById(messageId);
        if (message == null || message.getRiskLevel() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "风险事件不存在");
        }
        long handled = auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLogDO>()
                .eq(AuditLogDO::getResourceType, "MESSAGE")
                .eq(AuditLogDO::getResourceId, messageId)
                .in(AuditLogDO::getAction, RISK_DISPOSITION_ACTIONS));
        if (handled > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该风险事件已经处置");
        }

        String action = switch (request.action()) {
            case MARK_FALSE_POSITIVE -> "CHAT_RISK_FALSE_POSITIVE";
            case RECORD_REMINDER -> "CHAT_RISK_REMINDER_RECORDED";
            case RESTRICT_ACCOUNT -> "CHAT_RISK_ACCOUNT_RESTRICTED";
            case DISABLE_ACCOUNT -> "CHAT_RISK_ACCOUNT_DISABLED";
        };
        if (request.action() == RiskDispositionDTO.Action.RESTRICT_ACCOUNT
                || request.action() == RiskDispositionDTO.Action.DISABLE_ACCOUNT) {
            // 账号措施由管理员明确选择并复用现有状态服务，AI 只提供风险证据。
            UserStatus target = request.action() == RiskDispositionDTO.Action.DISABLE_ACCOUNT
                    ? UserStatus.DISABLED : UserStatus.RESTRICTED;
            updateUserStatus(adminId, message.getSenderId(),
                    new UserStatusDTO(target, request.reason()));
        }
        writeAudit(adminId, message.getSenderId(), action, "MESSAGE", messageId,
                message.getRiskLevel().name(), request.action().name(), null, request.reason());
    }

    private void resolveSellerDeposit(long disputeId, OrderDO order, boolean buyerWins) {
        if (order.getAuctionId() == null) {
            return;
        }
        depositMapper.selectHeldByAuction(order.getAuctionId()).stream()
                .filter(hold -> hold.getDepositType() == DepositType.SELLER)
                .findFirst()
                .ifPresent(hold -> settleSellerDeposit(disputeId, order, hold, buyerWins));
    }

    private void settleSellerDeposit(long disputeId, OrderDO order, DepositHoldDO hold,
                                     boolean buyerWins) {
        if (buyerWins) {
            walletService.transferFrozenToUser(order.getSellerId(), order.getBuyerId(),
                    hold.getAmount(), "dispute:" + disputeId + ":seller-deposit");
            hold.setStatus(DepositStatus.COMPENSATED);
        } else {
            walletService.releaseFrozen(order.getSellerId(), hold.getAmount(),
                    "dispute:" + disputeId + ":release-seller-deposit");
            hold.setStatus(DepositStatus.RELEASED);
        }
        depositMapper.updateById(hold);
    }

    private void adjustCredit(long actorId, long userId, int change, String reason,
                              String resourceType, long resourceId) {
        userMapper.adjustCredit(userId, change);
        writeAudit(actorId, userId, "CREDIT_CHANGED", resourceType, resourceId,
                null, Integer.toString(change), change, reason);
    }

    private void writeAudit(long actorId, Long subjectUserId, String action,
                            String resourceType, long resourceId, String beforeState,
                            String afterState, Integer changeValue, String reason) {
        // 人工状态、信用和资金处置必须与业务更新在同一事务内留下不可分离的审计事实。
        AuditLogDO audit = new AuditLogDO();
        audit.setActorId(actorId);
        audit.setSubjectUserId(subjectUserId);
        audit.setAction(action);
        audit.setResourceType(resourceType);
        audit.setResourceId(resourceId);
        audit.setBeforeState(beforeState);
        audit.setAfterState(afterState);
        audit.setChangeValue(changeValue);
        audit.setReason(reason);
        auditLogMapper.insert(audit);
    }
}
