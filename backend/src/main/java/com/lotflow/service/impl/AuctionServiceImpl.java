package com.lotflow.service.impl;

import com.lotflow.service.AuctionService;
import com.lotflow.service.ChannelService;
import com.lotflow.service.DepositService;
import com.lotflow.service.IdempotencyService;
import com.lotflow.service.OperationMetricsService;
import com.lotflow.service.OrderService;
import com.lotflow.service.OutboxService;
import com.lotflow.service.WalletService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lotflow.common.BusinessException;
import com.lotflow.common.AfterCommit;
import com.lotflow.common.Cursor;
import com.lotflow.common.CursorPageVO;
import com.lotflow.common.IdempotencyKey;
import com.lotflow.common.Money;
import com.lotflow.common.ResultCode;
import com.lotflow.domain.AuctionPolicy;
import com.lotflow.dto.PlaceBidDTO;
import com.lotflow.entity.AuctionDO;
import com.lotflow.entity.AuditLogDO;
import com.lotflow.entity.BidDO;
import com.lotflow.entity.DepositHoldDO;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.enums.DomainEnums.AuctionStatus;
import com.lotflow.entity.enums.DomainEnums.BidStatus;
import com.lotflow.entity.enums.DomainEnums.DepositStatus;
import com.lotflow.entity.enums.DomainEnums.DepositType;
import com.lotflow.entity.enums.DomainEnums.ListingStatus;
import com.lotflow.entity.enums.DomainEnums.SaleChannelStatus;
import com.lotflow.mapper.AuctionMapper;
import com.lotflow.mapper.AuditLogMapper;
import com.lotflow.mapper.BidMapper;
import com.lotflow.mapper.DepositHoldMapper;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.mapper.SaleChannelMapper;
import com.lotflow.mapper.UserMapper;
import com.lotflow.vo.AuctionVO;
import com.lotflow.vo.BidVO;
import com.lotflow.websocket.RealtimePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 拍卖创建、出价、结束和取消流程。 */
@Service
@RequiredArgsConstructor
public class AuctionServiceImpl implements AuctionService {

    private final AuctionMapper auctionMapper;
    private final BidMapper bidMapper;
    private final ListingMapper listingMapper;
    private final DepositHoldMapper depositMapper;
    private final SaleChannelMapper channelMapper;
    private final AuditLogMapper auditLogMapper;
    private final WalletService walletService;
    private final OrderService orderService;
    private final ChannelService channelService;
    private final DepositService depositService;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;
    private final RealtimePublisher realtimePublisher;
    private final UserMapper userMapper;
    private final OperationMetricsService metrics;

    /** 审核通过后创建拍卖并冻结卖家保证金。 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @Override
    public long createScheduledAuction(ListingDO listing) {
        OffsetDateTime now = auctionMapper.databaseTime();
        if (listing.getScheduledStart().isBefore(now)) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION,
                    "计划开拍时间已经过去，请重新排期");
        }
        // 开拍时间最多晚于审核通过后七天：用数据库时间比较，防止卖家长期占用已审核商品。
        if (listing.getScheduledStart().isAfter(now.plusDays(7))) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION,
                    "拍卖开始时间不能晚于审核通过后七天");
        }
        // 同一商品重新拍卖时 attemptNo 递增，历史场次和保证金赔付仍能按场次追溯。
        long attemptNo = auctionMapper.selectCount(new LambdaQueryWrapper<AuctionDO>()
                .eq(AuctionDO::getListingId, listing.getId())) + 1;
        BigDecimal sellerDeposit = Money.sellerDeposit(listing.getStartPrice());

        AuctionDO auction = new AuctionDO();
        auction.setListingId(listing.getId());
        auction.setSellerId(listing.getSellerId());
        auction.setAttemptNo((int) attemptNo);
        auction.setStartPrice(listing.getStartPrice());
        auction.setCurrentPrice(listing.getStartPrice());
        auction.setStatus(AuctionStatus.SCHEDULED);
        auction.setStartTime(listing.getScheduledStart());
        auction.setEndTime(listing.getScheduledStart().plusHours(listing.getDurationHours()));
        auction.setExtensionSeconds(0);
        auctionMapper.insert(auction);

        // 卖家保证金是成交履约担保：拍卖行、冻结流水和销售渠道必须同事务提交，不能产生无担保场次。
        walletService.freeze(listing.getSellerId(), sellerDeposit,
                "deposit:seller:" + auction.getId());
        DepositHoldDO hold = new DepositHoldDO();
        hold.setAuctionId(auction.getId());
        hold.setUserId(listing.getSellerId());
        hold.setDepositType(DepositType.SELLER);
        hold.setAmount(sellerDeposit);
        hold.setStatus(DepositStatus.HELD);
        hold.setBusinessKey("deposit:seller:" + auction.getId());
        depositMapper.insert(hold);

        channelService.createAuctionChannel(listing, auction.getStartTime(), auction.getEndTime());
        listing.setStatus(ListingStatus.SCHEDULED);
        listingMapper.updateById(listing);
        outboxService.append("auction.scheduled", "auction", auction.getId(),
                Map.of("auctionId", auction.getId(), "listingId", listing.getId()));
        return auction.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public BidVO placeBid(long bidderId, long auctionId, PlaceBidDTO request,
                          String rawIdempotencyKey) {
        String key = IdempotencyKey.require(rawIdempotencyKey);
        BigDecimal amount = parseMoney(request.amount());
        // 出价请求体与幂等键同时登记；同一请求重试时只能返回原出价，不能再次改变最高价和保证金。
        var claim = idempotencyService.claim("auction.bid", key,
                Map.of("auctionId", auctionId, "bidderId", bidderId, "amount", amount.toPlainString()));
        if (claim.completed()) {
            // 幂等重试直接返回首次结果，不能再次冻结保证金或生成新出价。
            BidDO existing = bidMapper.selectBid(claim.resourceId());
            AuctionDO auction = auctionMapper.selectById(auctionId);
            return BidVO.from(existing, auction);
        }

        // 价格、结束时间和最高出价必须基于同一拍卖行锁判断；先查后改会让多个买家看到相同旧最高价。
        AuctionDO auction = auctionMapper.selectByIdForUpdate(auctionId);
        if (auction == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "拍卖不存在");
        }
        OffsetDateTime now = auctionMapper.databaseTime();
        if (auction.getStatus() != AuctionStatus.LIVE
                || now.isBefore(auction.getStartTime())
                || !now.isBefore(auction.getEndTime())) {
            // 出价窗口由数据库时间决定，避免应用实例时钟漂移导致最后几秒的公平性争议。
            metrics.recordBidConflict();
            throw new BusinessException(ResultCode.CONFLICT, "拍卖不在有效出价时间内");
        }
        if (auction.getSellerId().equals(bidderId)) {
            // 自买自卖会伪造成交和信用，因此在锁定拍卖后直接拒绝卖家账号出价。
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "卖家不能竞拍自己的商品");
        }
        if (userMapper.selectById(bidderId).getCreditScore() < 40) {
            // 信用分低于 40 是平台交易限制：出价入口必须校验当前信用，不能只依赖前端提示。
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION,
                    "信用分低于 40，暂停出价");
        }
        BigDecimal minimum = auction.getCurrentPrice()
                .add(AuctionPolicy.minimumIncrement(auction.getCurrentPrice()));
        if (amount.compareTo(minimum) < 0) {
            // 最低加价规则统一交给 AuctionPolicy，出价失败返回最新价，避免系统自动替用户加价。
            metrics.recordBidConflict();
            throw new BusinessException(ResultCode.CONFLICT,
                    "出价至少为 " + minimum.toPlainString() + "，当前最高价为 "
                            + auction.getCurrentPrice().toPlainString());
        }

        // 买家保证金按新出价重算，但只补冻与当前持有额的差额，避免每次出价重复冻结整笔资金。
        BigDecimal requiredDeposit = Money.buyerDeposit(amount);
        DepositHoldDO hold = depositMapper.selectForUpdate(auctionId, bidderId, DepositType.BUYER);
        BigDecimal held = hold == null ? BigDecimal.ZERO.setScale(2) : hold.getAmount();
        if (requiredDeposit.compareTo(held) > 0) {
            walletService.freeze(bidderId, requiredDeposit.subtract(held),
                    "deposit:buyer:" + auctionId + ":" + bidderId + ":" + requiredDeposit.toPlainString());
        }
        if (hold == null) {
            hold = new DepositHoldDO();
            hold.setAuctionId(auctionId);
            hold.setUserId(bidderId);
            hold.setDepositType(DepositType.BUYER);
            hold.setAmount(requiredDeposit);
            hold.setStatus(DepositStatus.HELD);
            hold.setBusinessKey("deposit:buyer:" + auctionId + ":" + bidderId);
            depositMapper.insert(hold);
        } else if (requiredDeposit.compareTo(held) > 0) {
            hold.setAmount(requiredDeposit);
            depositMapper.updateById(hold);
        }

        BidDO bid = new BidDO();
        bid.setAuctionId(auctionId);
        bid.setBidderId(bidderId);
        bid.setAmount(amount);
        bid.setDepositRequired(requiredDeposit);
        bid.setIdempotencyKey(key);
        bid.setStatus(BidStatus.VALID);
        bidMapper.insert(bid);

        auction.setCurrentPrice(amount);
        auction.setHighestBidderId(bidderId);
        auction.setHighestBidId(bid.getId());
        extendIfNecessary(auction, now);
        auctionMapper.updateById(auction);
        idempotencyService.complete(claim, bid.getId());
        outboxService.append("auction.bid-placed", "auction", auctionId,
                Map.of("auctionId", auctionId, "bidId", bid.getId(),
                        "amount", amount, "endTime", auction.getEndTime()));
        AuctionVO auctionView = AuctionVO.from(auction);
        // 出价和最高价已经提交后才推送 WebSocket；实时通知失败不能把数据库中的有效出价改成失败。
        AfterCommit.run(() -> realtimePublisher.publish("/topic/auctions/" + auctionId,
                "BID_PLACED", Long.toString(auctionId), bid.getId(), auctionView));
        return BidVO.from(bid, auction);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void processAuction(long auctionId) {
        AuctionDO auction = auctionMapper.selectByIdForUpdate(auctionId);
        if (auction == null) {
            return;
        }
        OffsetDateTime now = auctionMapper.databaseTime();
        if (auction.getStatus() == AuctionStatus.SCHEDULED
                && !now.isBefore(auction.getStartTime())) {
            activate(auction);
            return;
        }
        if (auction.getStatus() == AuctionStatus.LIVE && !now.isBefore(auction.getEndTime())) {
            long started = System.nanoTime();
            try {
                close(auction, now);
                metrics.recordAuctionClose(Duration.ofNanos(System.nanoTime() - started), "success");
            } catch (RuntimeException exception) {
                metrics.recordAuctionClose(Duration.ofNanos(System.nanoTime() - started), "failure");
                throw exception;
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void cancel(long sellerId, long auctionId) {
        AuctionDO auction = auctionMapper.selectByIdForUpdate(auctionId);
        if (auction == null || !auction.getSellerId().equals(sellerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权取消该拍卖");
        }
        if (auction.getStatus() != AuctionStatus.SCHEDULED
                && auction.getStatus() != AuctionStatus.LIVE) {
            throw new BusinessException(ResultCode.CONFLICT, "拍卖当前不能取消");
        }
        List<DepositHoldDO> holds = depositMapper.selectHeldByAuction(auctionId);
        // 卖家取消拍卖先解除所有竞买人的冻结；首笔出价后卖家保证金再按违约规则赔付最高出价者。
        for (DepositHoldDO hold : holds) {
            if (hold.getDepositType() == DepositType.BUYER) {
                releaseHold(hold, "auction-cancel");
            }
        }
        DepositHoldDO sellerHold = holds.stream()
                .filter(hold -> hold.getDepositType() == DepositType.SELLER)
                .findFirst().orElse(null);
        if (auction.getHighestBidderId() != null && sellerHold != null) {
            // 首笔有效出价后取消属于卖家违约，保证金赔付最高出价者而不是原路解冻。
            walletService.transferFrozenToUser(sellerId, auction.getHighestBidderId(),
                    sellerHold.getAmount(), "auction:" + auctionId + ":seller-cancel-compensation");
            sellerHold.setStatus(DepositStatus.COMPENSATED);
            depositMapper.updateById(sellerHold);
            addCreditPenalty(sellerId, "SELLER_CANCEL", auctionId);
        } else if (sellerHold != null) {
            releaseHold(sellerHold, "auction-cancel");
        }

        auction.setStatus(AuctionStatus.CANCELLED);
        auctionMapper.updateById(auction);
        ListingDO listing = listingMapper.selectByIdForUpdate(auction.getListingId());
        listing.setStatus(ListingStatus.CANCELLED);
        listingMapper.updateById(listing);
        var channel = channelMapper.selectActiveForUpdate(listing.getId());
        if (channel != null) {
            channel.setStatus(SaleChannelStatus.CANCELLED);
            channelMapper.updateById(channel);
        }
        outboxService.append("auction.cancelled", "auction", auctionId,
                Map.of("auctionId", auctionId, "sellerId", sellerId));
    }

    @Transactional(readOnly = true)
    @Override
    public AuctionVO get(long auctionId) {
        AuctionDO auction = auctionMapper.selectById(auctionId);
        if (auction == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "拍卖不存在");
        }
        return AuctionVO.from(auction);
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<AuctionVO> list(String cursor, int requestedLimit, boolean activeOnly) {
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        Long cursorId = Cursor.parse(cursor);
        List<AuctionDO> rows = auctionMapper.selectPage(cursorId, limit + 1, activeOnly);
        boolean hasMore = rows.size() > limit;
        List<AuctionDO> selected = hasMore ? rows.subList(0, limit) : rows;
        String next = hasMore ? selected.getLast().getId().toString() : null;
        return new CursorPageVO<>(selected.stream().map(AuctionVO::from).toList(), next);
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<BidVO> bids(long auctionId, String cursor, int requestedLimit) {
        AuctionDO auction = auctionMapper.selectById(auctionId);
        if (auction == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "拍卖不存在");
        }
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        List<BidDO> rows = bidMapper.selectBidPage(auctionId, Cursor.parse(cursor), limit + 1);
        boolean hasMore = rows.size() > limit;
        List<BidDO> selected = hasMore ? rows.subList(0, limit) : rows;
        String next = hasMore ? selected.getLast().getId().toString() : null;
        return new CursorPageVO<>(selected.stream().map(bid -> BidVO.from(bid, auction)).toList(), next);
    }

    private void activate(AuctionDO auction) {
        // 到点开拍是可重试状态迁移：只有 SCHEDULED 场次进入 LIVE，并同步商品和销售渠道状态。
        auction.setStatus(AuctionStatus.LIVE);
        auctionMapper.updateById(auction);
        ListingDO listing = listingMapper.selectByIdForUpdate(auction.getListingId());
        listing.setStatus(ListingStatus.AUCTION_LIVE);
        listingMapper.updateById(listing);
        var channel = channelMapper.selectActiveForUpdate(listing.getId());
        if (channel != null) {
            channel.setStatus(SaleChannelStatus.ACTIVE);
            channelMapper.updateById(channel);
        }
        outboxService.append("auction.started", "auction", auction.getId(),
                Map.of("auctionId", auction.getId()));
        AuctionVO auctionView = AuctionVO.from(auction);
        AfterCommit.run(() -> realtimePublisher.publish("/topic/auctions/" + auction.getId(),
                "AUCTION_STARTED", Long.toString(auction.getId()),
                auction.getStartTime().toEpochSecond(), auctionView));
    }

    private void close(AuctionDO auction, OffsetDateTime now) {
        ListingDO listing = listingMapper.selectByIdForUpdate(auction.getListingId());
        List<DepositHoldDO> holds = depositMapper.selectHeldByAuction(auction.getId());
        if (auction.getHighestBidderId() == null) {
            // 流拍时释放所有保证金并进入卖家授权或待决定流程，不能默认生成低价订单。
            for (DepositHoldDO hold : holds) {
                releaseHold(hold, "auction-unsold");
            }
            auction.setStatus(AuctionStatus.UNSOLD);
            auctionMapper.updateById(auction);
            // 商品不保留"流拍"中间态：渠道记录流拍事实，最终状态由授权降级或卖家决定分支写入。
            var channel = channelMapper.selectActiveForUpdate(listing.getId());
            if (channel != null) {
                channel.setStatus(SaleChannelStatus.UNSOLD);
                channelMapper.updateById(channel);
            }
            channelService.onAuctionUnsold(listing, now);
            outboxService.append("auction.unsold", "auction", auction.getId(),
                    Map.of("auctionId", auction.getId()));
            return;
        }
        DepositHoldDO winnerHold = holds.stream()
                .filter(hold -> hold.getDepositType() == DepositType.BUYER
                        && hold.getUserId().equals(auction.getHighestBidderId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.CONFLICT, "中标者保证金不存在"));
        // 中标者保证金继续为中标签约担保，其他竞买人的冻结必须在同一关单事务内解除。
        for (DepositHoldDO hold : holds) {
            if (hold.getDepositType() == DepositType.BUYER
                    && !hold.getUserId().equals(auction.getHighestBidderId())) {
                releaseHold(hold, "auction-not-winner");
            }
        }
        auction.setStatus(AuctionStatus.ENDED);
        auctionMapper.updateById(auction);
        orderService.createAuctionOrder(auction, listing, winnerHold.getAmount(), now);
        var channel = channelMapper.selectActiveForUpdate(listing.getId());
        if (channel != null) {
            channel.setStatus(SaleChannelStatus.SOLD);
            channelMapper.updateById(channel);
        }
        outboxService.append("auction.ended", "auction", auction.getId(),
                Map.of("auctionId", auction.getId(), "winnerId", auction.getHighestBidderId()));
        AuctionVO auctionView = AuctionVO.from(auction);
        AfterCommit.run(() -> realtimePublisher.publish("/topic/auctions/" + auction.getId(),
                "AUCTION_ENDED", Long.toString(auction.getId()),
                auction.getEndTime().toEpochSecond(), auctionView));
    }

    private void releaseHold(DepositHoldDO hold, String reason) {
        walletService.releaseFrozen(hold.getUserId(), hold.getAmount(),
                "deposit:" + reason + ":" + hold.getId());
        hold.setStatus(DepositStatus.RELEASED);
        depositMapper.updateById(hold);
    }

    private void addCreditPenalty(long userId, String reason, long relatedId) {
        userMapper.adjustCredit(userId, -15);
        AuditLogDO record = new AuditLogDO();
        record.setSubjectUserId(userId);
        record.setChangeValue(-15);
        record.setReason(reason);
        record.setAction("CREDIT_CHANGED");
        record.setResourceType("AUCTION");
        record.setResourceId(relatedId);
        auditLogMapper.insert(record);
    }

    private void extendIfNecessary(AuctionDO auction, OffsetDateTime now) {
        AuctionPolicy.Extension extension = AuctionPolicy.extension(
                auction.getEndTime(), auction.getExtensionSeconds(), now);
        auction.setEndTime(extension.endTime());
        auction.setExtensionSeconds(extension.extensionSeconds());
    }

    private BigDecimal parseMoney(String value) {
        try {
            return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "金额必须最多两位小数");
        }
    }

}
