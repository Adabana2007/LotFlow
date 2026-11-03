package com.lotflow.service.impl;

import com.lotflow.common.BusinessException;
import com.lotflow.common.Cursor;
import com.lotflow.common.CursorPageVO;
import com.lotflow.common.ResultCode;
import com.lotflow.entity.enums.DomainEnums.ListingStatus;
import com.lotflow.entity.enums.DomainEnums.OrderStatus;
import com.lotflow.mapper.PersonalWorkspaceMapper;
import com.lotflow.service.PersonalWorkspaceService;
import com.lotflow.vo.BidHistoryVO;
import com.lotflow.vo.ConversationSummaryVO;
import com.lotflow.vo.DisputeDetailVO;
import com.lotflow.vo.DisputeTimelineVO;
import com.lotflow.vo.DisputeVO;
import com.lotflow.vo.ListingVO;
import com.lotflow.vo.OrderSummaryVO;
import com.lotflow.vo.TodoItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** 个人工作台读取模型；所有写操作仍由各自领域服务负责。 */
@Service
@RequiredArgsConstructor
public class PersonalWorkspaceServiceImpl implements PersonalWorkspaceService {

    private static final Set<String> ORDER_ROLES = Set.of("ALL", "BUYER", "SELLER");
    private static final Set<String> ORDER_STATUSES = Set.of(
            OrderStatus.PENDING_PAYMENT.name(), OrderStatus.PAID.name(),
            OrderStatus.SHIPPED.name(), OrderStatus.RECEIVED.name(),
            OrderStatus.DISPUTED.name(), OrderStatus.COMPLETED.name(),
            OrderStatus.BUYER_DEFAULTED.name(), OrderStatus.CANCELLED.name(),
            OrderStatus.SECOND_CHANCE.name());
    private static final Set<String> LISTING_STATUSES = Set.of(
            java.util.Arrays.stream(ListingStatus.values()).map(Enum::name).toArray(String[]::new));

    private final PersonalWorkspaceMapper mapper;

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<OrderSummaryVO> orders(long userId, String cursor, String role,
                                                String status, int requestedLimit) {
        String normalizedRole = normalizeRole(role);
        String normalizedStatus = normalize(status, ORDER_STATUSES, "订单状态");
        int limit = bounded(requestedLimit);
        List<OrderSummaryVO> rows = mapper.selectOrders(userId, normalizedRole,
                normalizedStatus, Cursor.parse(cursor), limit + 1);
        return page(rows, limit, row -> row.getId());
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<ListingVO> sellerListings(long sellerId, String cursor,
                                                   String status, int requestedLimit) {
        String normalizedStatus = normalize(status, LISTING_STATUSES, "商品状态");
        int limit = bounded(requestedLimit);
        List<ListingVO> rows = mapper.selectSellerListings(sellerId, normalizedStatus,
                        Cursor.parse(cursor), limit + 1).stream()
                .map(ListingVO::from)
                .toList();
        return page(rows, limit, ListingVO::id);
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<BidHistoryVO> bidHistory(long bidderId, String cursor,
                                                 int requestedLimit) {
        int limit = bounded(requestedLimit);
        List<BidHistoryVO> rows = mapper.selectBidHistory(bidderId, Cursor.parse(cursor), limit + 1);
        return page(rows, limit, BidHistoryVO::getId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<TodoItemVO> todos(long userId) {
        return mapper.selectTodos(userId);
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<ConversationSummaryVO> conversations(long userId, String cursor,
                                                             int requestedLimit) {
        int limit = bounded(requestedLimit);
        List<ConversationSummaryVO> rows = mapper.selectConversations(userId,
                Cursor.parse(cursor), limit + 1);
        return page(rows, limit, ConversationSummaryVO::getId);
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<DisputeVO> disputes(long userId, boolean privileged,
                                             String cursor, int requestedLimit) {
        int limit = bounded(requestedLimit);
        List<DisputeVO> rows = mapper.selectDisputes(userId, privileged,
                Cursor.parse(cursor), limit + 1);
        return page(rows, limit, DisputeVO::getId);
    }

    @Transactional(readOnly = true)
    @Override
    public DisputeDetailVO dispute(long userId, boolean privileged, long orderId) {
        DisputeVO dispute = mapper.selectDispute(userId, privileged, orderId);
        if (dispute == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "争议不存在");
        }
        return new DisputeDetailVO(dispute, timeline(dispute));
    }

    private List<DisputeTimelineVO> timeline(DisputeVO dispute) {
        List<DisputeTimelineVO> timeline = new ArrayList<>();
        add(timeline, "ORDER_CREATED", "订单创建", "交易事实开始记录", dispute.getCreatedAt());
        add(timeline, "PAYMENT", "钱包付款完成", "货款进入平台担保账户", dispute.getPaidAt());
        add(timeline, "SHIPMENT", "卖家已发货", "订单进入物流履约阶段", dispute.getShippedAt());
        add(timeline, "RECEIPT", "买家确认收货", "售后争议窗口开始计时", dispute.getReceivedAt());
        add(timeline, "DISPUTE_OPENED", "买家发起争议", dispute.getReason(), dispute.getDisputeOpenedAt());
        add(timeline, "DISPUTE_RESOLVED", "人工完成裁决", dispute.getResolution(), dispute.getResolvedAt());
        timeline.sort(Comparator.comparing(DisputeTimelineVO::occurredAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(timeline);
    }

    private void add(List<DisputeTimelineVO> timeline, String type, String title,
                     String detail, OffsetDateTime occurredAt) {
        if (occurredAt != null) {
            timeline.add(new DisputeTimelineVO(type, title, detail, occurredAt));
        }
    }

    private String normalizeRole(String role) {
        String value = role == null || role.isBlank() ? "ALL" : role.trim().toUpperCase();
        if (!ORDER_ROLES.contains(value)) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "订单角色筛选不合法");
        }
        return "ALL".equals(value) ? null : value;
    }

    private String normalize(String value, Set<String> allowed, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, label + "筛选不合法");
        }
        return normalized;
    }

    private int bounded(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private <T> CursorPageVO<T> page(List<T> rows, int limit,
                                     java.util.function.Function<T, String> id) {
        boolean hasMore = rows.size() > limit;
        List<T> selected = hasMore ? rows.subList(0, limit) : rows;
        String next = hasMore ? id.apply(selected.getLast()) : null;
        return new CursorPageVO<>(List.copyOf(selected), next);
    }
}
