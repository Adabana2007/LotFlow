package com.lotflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lotflow.common.BusinessException;
import com.lotflow.common.IdempotencyKey;
import com.lotflow.common.Money;
import com.lotflow.common.ResultCode;
import com.lotflow.dto.DisputeDTO;
import com.lotflow.dto.ShipmentDTO;
import com.lotflow.entity.AuctionDO;
import com.lotflow.entity.AuditLogDO;
import com.lotflow.entity.BidDO;
import com.lotflow.entity.DepositHoldDO;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.OrderDO;
import com.lotflow.entity.enums.DomainEnums.ChannelDecisionStatus;
import com.lotflow.entity.enums.DomainEnums.DepositStatus;
import com.lotflow.entity.enums.DomainEnums.DepositType;
import com.lotflow.entity.enums.DomainEnums.DisputeStatus;
import com.lotflow.entity.enums.DomainEnums.ListingStatus;
import com.lotflow.entity.enums.DomainEnums.OrderStatus;
import com.lotflow.entity.enums.DomainEnums.ReservationStatus;
import com.lotflow.entity.enums.DomainEnums.SaleChannelStatus;
import com.lotflow.mapper.AuditLogMapper;
import com.lotflow.mapper.BidMapper;
import com.lotflow.mapper.DatabaseTimeMapper;
import com.lotflow.mapper.DepositHoldMapper;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.mapper.OrderMapper;
import com.lotflow.mapper.SaleChannelMapper;
import com.lotflow.mapper.UserMapper;
import com.lotflow.service.ChannelService;
import com.lotflow.service.DepositService;
import com.lotflow.service.IdempotencyService;
import com.lotflow.service.OrderService;
import com.lotflow.service.OutboxService;
import com.lotflow.service.WalletService;
import com.lotflow.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 订单支付、担保、履约、争议和超时处理。 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ListingMapper listingMapper;
    private final SaleChannelMapper saleChannelMapper;
    private final DatabaseTimeMapper timeMapper;
    private final WalletService walletService;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;
    private final DepositService depositService;
    private final BidMapper bidMapper;
    private final DepositHoldMapper depositMapper;
    private final UserMapper userMapper;
    private final AuditLogMapper auditLogMapper;
    private final ChannelService channelService;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @Override
    public OrderDO createAuctionOrder(AuctionDO auction, ListingDO listing,
                                      BigDecimal buyerDeposit, OffsetDateTime now) {
        OrderDO order = baseOrder(listing, auction.getHighestBidderId(), "AUCTION",
                auction.getCurrentPrice(), now.plusHours(24));
        order.setAuctionId(auction.getId());
        order.setDepositApplied(buyerDeposit);
        orderMapper.insert(order);
        listing.setStatus(ListingStatus.SOLD_PENDING_PAYMENT);
        listingMapper.updateById(listing);
        outboxService.append("order.created", "order", order.getId(),
                Map.of("orderId", order.getId(), "buyerId", order.getBuyerId()));
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderVO pay(long buyerId, long orderId, String rawIdempotencyKey) {
        String key = IdempotencyKey.require(rawIdempotencyKey);
        requireBuyer(requireOrder(orderId), buyerId);
        var claim = idempotencyService.claim("order.pay", key,
                Map.of("orderId", orderId, "buyerId", buyerId));
        if (claim.completed()) {
            return OrderVO.from(requireOrder(claim.resourceId()));
        }

        OrderDO order = orderMapper.selectByIdForUpdate(orderId);
        requireBuyer(order, buyerId);
        OffsetDateTime now = timeMapper.now();
        requirePayable(order, now);
        BigDecimal deposit = zeroIfNull(order.getDepositApplied());
        if (order.getStatus() == OrderStatus.SECOND_CHANCE) {
            // 第二高买家只有付款动作才接受顺延要约，创建资格时不能提前冻结其资金。
            acceptSecondChanceDeposit(order, buyerId, deposit);
        }

        transferPaymentToEscrow(order, buyerId, deposit);
        consumeBuyerDeposit(order);
        order.setPaymentKey(key);
        order.setPaidAt(now);
        order.setStatus(OrderStatus.PAID);
        order.setShipDeadline(now.plusHours(72));
        order.setEscrowAmount(order.getAmount());
        if (order.getReservationStatus() == ReservationStatus.RESERVED) {
            order.setReservationStatus(ReservationStatus.ORDERED);
        }
        orderMapper.updateById(order);
        updateChannelSold(order);
        idempotencyService.complete(claim, orderId);
        outboxService.append("order.paid", "order", orderId,
                Map.of("orderId", orderId, "amount", order.getAmount()));
        return OrderVO.from(order);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderVO ship(long sellerId, long orderId, ShipmentDTO request) {
        OrderDO order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null || !order.getSellerId().equals(sellerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权发货");
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ResultCode.CONFLICT, "订单当前不能发货");
        }
        OffsetDateTime now = timeMapper.now();
        order.setCarrier(request.carrier());
        order.setTrackingNo(request.trackingNo());
        order.setShippedAt(now);
        order.setReceiptDeadline(now.plusDays(7));
        order.setStatus(OrderStatus.SHIPPED);
        orderMapper.updateById(order);
        outboxService.append("order.shipped", "order", orderId, Map.of("orderId", orderId));
        return OrderVO.from(order);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderVO confirmReceipt(long buyerId, long orderId) {
        OrderDO order = orderMapper.selectByIdForUpdate(orderId);
        requireBuyer(order, buyerId);
        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new BusinessException(ResultCode.CONFLICT, "订单当前不能确认收货");
        }
        OffsetDateTime now = timeMapper.now();
        // 确认收货只开启售后窗口，担保货款仍需等争议期结束后结算。
        order.setReceivedAt(now);
        order.setDisputeDeadline(now.plusDays(7));
        order.setStatus(OrderStatus.RECEIVED);
        orderMapper.updateById(order);
        outboxService.append("order.received", "order", orderId, Map.of("orderId", orderId));
        return OrderVO.from(order);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderVO openDispute(long buyerId, long orderId, DisputeDTO request) {
        OrderDO order = orderMapper.selectByIdForUpdate(orderId);
        requireBuyer(order, buyerId);
        OffsetDateTime now = timeMapper.now();
        if (order.getStatus() != OrderStatus.RECEIVED || order.getDisputeDeadline() == null
                || !order.getDisputeDeadline().isAfter(now)) {
            throw new BusinessException(ResultCode.CONFLICT, "已超过争议发起期限");
        }
        // 争议事实直接归属订单；后台路径中的 disputeId 就是该订单 ID。
        order.setStatus(OrderStatus.DISPUTED);
        order.setDisputeStatus(DisputeStatus.OPEN);
        order.setDisputeReason(request.reason());
        order.setDisputeEvidence(request.evidenceSummary());
        order.setDisputeOpenedAt(now);
        orderMapper.updateById(order);
        AuditLogDO audit = new AuditLogDO();
        audit.setActorId(buyerId);
        audit.setSubjectUserId(order.getSellerId());
        audit.setAction("DISPUTE_OPENED");
        audit.setResourceType("ORDER");
        audit.setResourceId(orderId);
        audit.setBeforeState(OrderStatus.RECEIVED.name());
        audit.setAfterState(OrderStatus.DISPUTED.name());
        audit.setReason(request.reason());
        auditLogMapper.insert(audit);
        ListingDO listing = listingMapper.selectByIdForUpdate(order.getListingId());
        if (listing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单对应商品不存在");
        }
        listing.setStatus(ListingStatus.DISPUTED);
        listingMapper.updateById(listing);
        outboxService.append("order.dispute-opened", "order", orderId,
                Map.of("orderId", orderId, "disputeId", orderId));
        return OrderVO.from(order);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @Override
    public OrderDO createRetailOrder(ListingDO listing, long buyerId, BigDecimal amount,
                                     String sourceType, OffsetDateTime paymentDeadline) {
        OrderDO order = baseOrder(listing, buyerId, sourceType, amount, paymentDeadline);
        orderMapper.insert(order);
        listing.setStatus(ListingStatus.SOLD_PENDING_PAYMENT);
        listingMapper.updateById(listing);
        outboxService.append("order.created", "order", order.getId(),
                Map.of("orderId", order.getId(), "sourceType", sourceType));
        return order;
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @Override
    public OrderDO createSeckillOrder(ListingDO listing, long buyerId, BigDecimal amount,
                                      OffsetDateTime paymentDeadline, String reservationKey) {
        OrderDO order = baseOrder(listing, buyerId, "SECKILL", amount, paymentDeadline);
        // 预占字段与订单一次插入，数据库约束可以拒绝任何缺少超时边界的秒杀订单。
        order.setReservationStatus(ReservationStatus.RESERVED);
        order.setReservationExpiresAt(paymentDeadline);
        order.setReservationKey(reservationKey);
        orderMapper.insert(order);
        listing.setStatus(ListingStatus.SOLD_PENDING_PAYMENT);
        listingMapper.updateById(listing);
        outboxService.append("order.created", "order", order.getId(),
                Map.of("orderId", order.getId(), "sourceType", "SECKILL"));
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void processPendingPayment(long orderId) {
        OrderDO order = orderMapper.selectByIdForUpdate(orderId);
        if (!isPendingPayment(order)) {
            return;
        }
        OffsetDateTime now = timeMapper.now();
        if (isAfter(order.getPaymentDeadline(), now)) {
            return;
        }
        if (isRetail(order)) {
            cancelRetailReservation(order, now);
            return;
        }
        if (order.getStatus() == OrderStatus.SECOND_CHANCE) {
            expireSecondChance(order, now);
            return;
        }
        defaultAuctionBuyer(order, now);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderVO offerSecondChance(long sellerId, long defaultedOrderId) {
        OrderDO defaulted = orderMapper.selectByIdForUpdate(defaultedOrderId);
        requireSecondChanceSource(defaulted, sellerId);
        BidDO second = bidMapper.selectSecondHighest(defaulted.getAuctionId());
        if (second == null) {
            throw new BusinessException(ResultCode.CONFLICT, "没有可顺延的第二高出价者");
        }
        BigDecimal deposit = Money.buyerDeposit(second.getAmount());
        OffsetDateTime now = timeMapper.now();
        OrderDO order = baseOrder(requireListingForUpdate(defaulted.getListingId()),
                second.getBidderId(), "AUCTION", second.getAmount(), now.plusHours(12));
        order.setAuctionId(defaulted.getAuctionId());
        order.setDepositApplied(deposit);
        order.setStatus(OrderStatus.SECOND_CHANCE);
        orderMapper.insert(order);

        defaulted.setOfferedSecondChance(true);
        orderMapper.updateById(defaulted);
        ListingDO listing = requireListingForUpdate(defaulted.getListingId());
        listing.setStatus(ListingStatus.SOLD_PENDING_PAYMENT);
        listingMapper.updateById(listing);
        var channel = saleChannelMapper.selectLatestForUpdate(listing.getId());
        if (channel != null) {
            channel.setStatus(SaleChannelStatus.SOLD);
            channel.setOrderId(order.getId());
            channel.setDecisionStatus(ChannelDecisionStatus.DECIDED);
            saleChannelMapper.updateById(channel);
        }
        outboxService.append("order.second-chance-offered", "order", order.getId(),
                Map.of("orderId", order.getId(), "buyerId", order.getBuyerId()));
        return OrderVO.from(order);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void autoConfirmReceipt(long orderId) {
        OrderDO order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null || order.getStatus() != OrderStatus.SHIPPED) {
            return;
        }
        OffsetDateTime now = timeMapper.now();
        if (isAfter(order.getReceiptDeadline(), now)) {
            return;
        }
        order.setReceivedAt(now);
        order.setDisputeDeadline(now.plusDays(7));
        order.setStatus(OrderStatus.RECEIVED);
        orderMapper.updateById(order);
        outboxService.append("order.auto-received", "order", orderId, Map.of("orderId", orderId));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void processSellerShipTimeout(long orderId) {
        OrderDO order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null || order.getStatus() != OrderStatus.PAID) {
            return;
        }
        OffsetDateTime now = timeMapper.now();
        if (isAfter(order.getShipDeadline(), now)) {
            return;
        }
        walletService.transferEscrowToAvailable(order.getBuyerId(), order.getEscrowAmount(),
                "order:" + orderId + ":seller-timeout-refund");
        compensateSellerDeposit(order);
        order.setStatus(OrderStatus.CANCELLED);
        orderMapper.updateById(order);
        adjustCredit(order.getSellerId(), -20, "SELLER_SHIP_TIMEOUT", orderId);
        ListingDO listing = requireListingForUpdate(order.getListingId());
        listing.setStatus(ListingStatus.RETURNED);
        listingMapper.updateById(listing);
        closeLatestChannel(listing.getId());
        outboxService.append("order.seller-ship-timeout", "order", orderId,
                Map.of("orderId", orderId, "sellerId", order.getSellerId()));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void completeAfterDisputeWindow(long orderId) {
        OrderDO order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null || order.getStatus() != OrderStatus.RECEIVED) {
            return;
        }
        OffsetDateTime now = timeMapper.now();
        if (isAfter(order.getDisputeDeadline(), now)) {
            return;
        }
        // 争议期结束才释放担保货款和卖家保证金，自动收货不能绕过售后保护。
        walletService.transferEscrowToAvailable(order.getSellerId(), order.getEscrowAmount(),
                "order:" + orderId + ":settle");
        depositService.releaseSellerDeposit(order.getAuctionId());
        order.setStatus(OrderStatus.COMPLETED);
        orderMapper.updateById(order);
        ListingDO listing = requireListingForUpdate(order.getListingId());
        listing.setStatus(ListingStatus.COMPLETED);
        listingMapper.updateById(listing);
        adjustCredit(order.getSellerId(), 1, "ORDER_COMPLETED", orderId);
        adjustCredit(order.getBuyerId(), 1, "ORDER_COMPLETED", orderId);
        outboxService.append("order.completed", "order", orderId, Map.of("orderId", orderId));
    }

    @Transactional(readOnly = true)
    @Override
    public OrderVO get(long userId, boolean privileged, long orderId) {
        OrderDO order = requireOrder(orderId);
        if (!privileged && !order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该订单");
        }
        return OrderVO.from(order);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderVO> listMine(long userId) {
        return orderMapper.selectList(new LambdaQueryWrapper<OrderDO>()
                        .and(query -> query.eq(OrderDO::getBuyerId, userId)
                                .or().eq(OrderDO::getSellerId, userId))
                        .orderByDesc(OrderDO::getId)
                        .last("limit 20"))
                .stream().map(OrderVO::from).toList();
    }

    private OrderDO baseOrder(ListingDO listing, long buyerId, String sourceType,
                              BigDecimal amount, OffsetDateTime paymentDeadline) {
        OrderDO order = new OrderDO();
        order.setOrderNo(newOrderNo());
        order.setListingId(listing.getId());
        order.setSellerId(listing.getSellerId());
        order.setBuyerId(buyerId);
        order.setSourceType(sourceType);
        order.setAmount(amount);
        order.setDepositApplied(BigDecimal.ZERO.setScale(2));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentDeadline(paymentDeadline);
        order.setEscrowAmount(BigDecimal.ZERO.setScale(2));
        order.setOfferedSecondChance(false);
        return order;
    }

    private void requirePayable(OrderDO order, OffsetDateTime now) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                && order.getStatus() != OrderStatus.SECOND_CHANCE) {
            throw new BusinessException(ResultCode.CONFLICT, "订单当前不能付款");
        }
        if (!isAfter(order.getPaymentDeadline(), now)) {
            throw new BusinessException(ResultCode.CONFLICT, "订单已超过付款时间");
        }
    }

    private void transferPaymentToEscrow(OrderDO order, long buyerId, BigDecimal deposit) {
        BigDecimal remaining = order.getAmount().subtract(deposit);
        if (remaining.signum() < 0) {
            throw new BusinessException(ResultCode.CONFLICT, "订单保证金不能高于成交价");
        }
        if (deposit.signum() > 0) {
            walletService.moveFrozenToEscrow(buyerId, deposit,
                    "order:" + order.getId() + ":deposit");
        }
        if (remaining.signum() > 0) {
            walletService.moveAvailableToEscrow(buyerId, remaining,
                    "order:" + order.getId() + ":pay");
        }
    }

    private void acceptSecondChanceDeposit(OrderDO order, long buyerId, BigDecimal required) {
        if (required.signum() == 0 || order.getAuctionId() == null) {
            return;
        }
        DepositHoldDO hold = depositMapper.selectForUpdate(order.getAuctionId(), buyerId,
                DepositType.BUYER);
        BigDecimal held = hold != null && hold.getStatus() == DepositStatus.HELD
                ? hold.getAmount() : BigDecimal.ZERO.setScale(2);
        if (required.compareTo(held) > 0) {
            walletService.freeze(buyerId, required.subtract(held),
                    "deposit:second-chance:" + order.getId() + ":accept");
        }
        if (hold == null) {
            hold = new DepositHoldDO();
            hold.setAuctionId(order.getAuctionId());
            hold.setUserId(buyerId);
            hold.setDepositType(DepositType.BUYER);
            hold.setBusinessKey("deposit:buyer:" + order.getAuctionId() + ":" + buyerId);
            hold.setStatus(DepositStatus.HELD);
            hold.setAmount(required);
            depositMapper.insert(hold);
        } else {
            hold.setStatus(DepositStatus.HELD);
            hold.setAmount(required);
            depositMapper.updateById(hold);
        }
    }

    private void consumeBuyerDeposit(OrderDO order) {
        if (order.getAuctionId() == null || zeroIfNull(order.getDepositApplied()).signum() == 0) {
            return;
        }
        DepositHoldDO hold = depositMapper.selectForUpdate(order.getAuctionId(), order.getBuyerId(),
                DepositType.BUYER);
        if (hold != null && hold.getStatus() == DepositStatus.HELD) {
            // 保证金已转入担保账户，标记后禁止关单或补偿再次释放同一笔冻结。
            hold.setStatus(DepositStatus.CONSUMED);
            depositMapper.updateById(hold);
        }
    }

    private void defaultAuctionBuyer(OrderDO order, OffsetDateTime now) {
        depositService.moveBuyerDepositToSeller(order);
        adjustCredit(order.getBuyerId(), -10, "BUYER_PAYMENT_DEFAULT", order.getId());
        order.setStatus(OrderStatus.BUYER_DEFAULTED);
        orderMapper.updateById(order);
        ListingDO listing = requireListingForUpdate(order.getListingId());
        var channel = saleChannelMapper.selectActiveForUpdate(listing.getId());
        if (channel != null) {
            channel.setStatus(SaleChannelStatus.UNSOLD);
            channel.setOrderId(null);
            saleChannelMapper.updateById(channel);
        }
        channelService.onAuctionBuyerDefaulted(listing, now);
        outboxService.append("order.buyer-defaulted", "order", order.getId(),
                Map.of("orderId", order.getId(), "sellerId", order.getSellerId()));
    }

    private void expireSecondChance(OrderDO order, OffsetDateTime now) {
        order.setStatus(OrderStatus.CANCELLED);
        orderMapper.updateById(order);
        ListingDO listing = requireListingForUpdate(order.getListingId());
        closeLatestChannel(listing.getId());
        depositService.releaseSellerDeposit(order.getAuctionId());
        channelService.onAuctionUnsold(listing, now);
        outboxService.append("order.second-chance-expired", "order", order.getId(),
                Map.of("orderId", order.getId(), "buyerId", order.getBuyerId()));
    }

    private void cancelRetailReservation(OrderDO order, OffsetDateTime now) {
        order.setStatus(OrderStatus.CANCELLED);
        if (order.getReservationStatus() == ReservationStatus.RESERVED) {
            order.setReservationStatus(ReservationStatus.EXPIRED);
        }
        orderMapper.updateById(order);
        ListingDO listing = requireListingForUpdate(order.getListingId());
        var channel = saleChannelMapper.selectActiveForUpdate(listing.getId());
        if (channel != null && channel.getEndsAt().isAfter(now)) {
            channel.setStatus(SaleChannelStatus.ACTIVE);
            channel.setOrderId(null);
            saleChannelMapper.updateById(channel);
            listing.setStatus("SECKILL".equals(order.getSourceType())
                    ? ListingStatus.SECKILL : ListingStatus.SHOP);
            listingMapper.updateById(listing);
        } else {
            if (channel != null) {
                channel.setStatus(SaleChannelStatus.UNSOLD);
                channel.setOrderId(null);
                saleChannelMapper.updateById(channel);
            }
            if ("SECKILL".equals(order.getSourceType())
                    && Boolean.TRUE.equals(listing.getIsAutoDowngrade())) {
                channelService.startShopAfterSeckill(listing, now);
            } else {
                listing.setStatus(ListingStatus.RETURNED);
                listingMapper.updateById(listing);
            }
        }
        outboxService.append("order.retail-expired", "order", order.getId(),
                Map.of("orderId", order.getId()));
    }

    private void compensateSellerDeposit(OrderDO order) {
        if (order.getAuctionId() == null) {
            return;
        }
        depositMapper.selectHeldByAuction(order.getAuctionId()).stream()
                .filter(hold -> hold.getDepositType() == DepositType.SELLER)
                .findFirst().ifPresent(hold -> {
                    walletService.transferFrozenToUser(order.getSellerId(), order.getBuyerId(),
                            hold.getAmount(), "order:" + order.getId() + ":seller-timeout-deposit");
                    hold.setStatus(DepositStatus.COMPENSATED);
                    depositMapper.updateById(hold);
                });
    }

    private void updateChannelSold(OrderDO order) {
        var channel = saleChannelMapper.selectActiveForUpdate(order.getListingId());
        if (channel != null) {
            channel.setStatus(SaleChannelStatus.SOLD);
            channel.setOrderId(order.getId());
            saleChannelMapper.updateById(channel);
        }
    }

    private void closeLatestChannel(long listingId) {
        var channel = saleChannelMapper.selectLatestForUpdate(listingId);
        if (channel != null) {
            channel.setStatus(SaleChannelStatus.UNSOLD);
            channel.setOrderId(null);
            saleChannelMapper.updateById(channel);
        }
    }

    private OrderDO requireOrder(Long orderId) {
        OrderDO order = orderId == null ? null : orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private ListingDO requireListingForUpdate(long listingId) {
        ListingDO listing = listingMapper.selectByIdForUpdate(listingId);
        if (listing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        return listing;
    }

    private void requireBuyer(OrderDO order, long buyerId) {
        if (order == null || !order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该订单");
        }
    }

    private void requireSecondChanceSource(OrderDO order, long sellerId) {
        if (order == null || !order.getSellerId().equals(sellerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权处理该订单");
        }
        if (order.getStatus() != OrderStatus.BUYER_DEFAULTED
                || order.getAuctionId() == null
                || Boolean.TRUE.equals(order.getOfferedSecondChance())) {
            throw new BusinessException(ResultCode.CONFLICT, "该订单不能顺延第二高出价者");
        }
    }

    private boolean isPendingPayment(OrderDO order) {
        return order != null && (order.getStatus() == OrderStatus.PENDING_PAYMENT
                || order.getStatus() == OrderStatus.SECOND_CHANCE);
    }

    private boolean isRetail(OrderDO order) {
        return "SECKILL".equals(order.getSourceType()) || "SHOP".equals(order.getSourceType());
    }

    private boolean isAfter(OffsetDateTime deadline, OffsetDateTime now) {
        return deadline != null && deadline.isAfter(now);
    }

    private BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(2) : amount;
    }

    private String newOrderNo() {
        return "O" + java.time.format.DateTimeFormatter.BASIC_ISO_DATE
                .format(java.time.LocalDate.now(java.time.ZoneOffset.UTC))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private void adjustCredit(long userId, int change, String reason, long relatedId) {
        userMapper.adjustCredit(userId, change);
        AuditLogDO record = new AuditLogDO();
        record.setSubjectUserId(userId);
        record.setAction("CREDIT_CHANGED");
        record.setChangeValue(change);
        record.setReason(reason);
        record.setResourceType("ORDER");
        record.setResourceId(relatedId);
        auditLogMapper.insert(record);
    }
}
