package com.lotflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lotflow.common.BusinessException;
import com.lotflow.common.Cursor;
import com.lotflow.common.CursorPageVO;
import com.lotflow.common.ResultCode;
import com.lotflow.dto.CreateListingDTO;
import com.lotflow.dto.ListingImageDTO;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.ListingImageDO;
import com.lotflow.entity.UserDO;
import com.lotflow.entity.enums.DomainEnums.ListingStatus;
import com.lotflow.entity.enums.DomainEnums.ReviewStatus;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.mapper.UserMapper;
import com.lotflow.mapper.DatabaseTimeMapper;
import com.lotflow.service.AiTaskService;
import com.lotflow.service.ListingService;
import com.lotflow.service.MediaStorageService;
import com.lotflow.service.OutboxService;
import com.lotflow.service.PersonalWorkspaceService;
import com.lotflow.vo.AdminListingDetailVO;
import com.lotflow.vo.AdminListingImageVO;
import com.lotflow.vo.ImageUrlVO;
import com.lotflow.vo.ListingVO;
import com.lotflow.vo.SellerListingImageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 商品从草稿、图片快照到提交 AI 审核的业务状态编排。 */
@Service
@RequiredArgsConstructor
public class ListingServiceImpl implements ListingService {

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> CATEGORY_CODES = Set.of(
            "CAMERA", "CARD", "FIGURE", "INSTRUMENT", "GAME_CONSOLE",
            "HANDICRAFT", "COLLECTIBLE", "FASHION", "HOME", "OTHER");
    private static final Set<String> CONDITION_CODES = Set.of(
            "NEW", "LIKE_NEW", "GOOD", "FAIR", "POOR", "FOR_PARTS");
    private static final Set<ListingStatus> PUBLIC_STATUSES = Set.of(
            ListingStatus.SCHEDULED, ListingStatus.AUCTION_LIVE,
            ListingStatus.SECKILL, ListingStatus.SHOP);

    private final ListingMapper listingMapper;
    private final ListingImageJsonCodec imageCodec;
    private final UserMapper userMapper;
    private final DatabaseTimeMapper timeMapper;
    private final OutboxService outboxService;
    private final AiTaskService aiTaskService;
    private final MediaStorageService mediaStorageService;
    private final PersonalWorkspaceService personalWorkspaceService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public long createDraft(long sellerId, CreateListingDTO request) {
        BigDecimal startPrice = parseMoney(request.startPrice());
        validateCodes(request.categoryCode(), request.conditionCode());
        OffsetDateTime now = timeMapper.now();
        if (startPrice.signum() <= 0 || request.scheduledStart().isBefore(now)) {
            // 价格和时间在进入草稿前校验，避免非法底价在后续自动降级时才暴露。
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "起拍价必须大于零且开拍时间不能早于当前时间");
        }
        BigDecimal seckillPrice = parseOptionalMoney(request.seckillPrice());
        BigDecimal shopPrice = parseOptionalMoney(request.shopPrice());
        validateDowngrade(request.autoDowngrade(), startPrice, seckillPrice, shopPrice);

        ListingDO listing = buildDraft(sellerId, request, startPrice, seckillPrice, shopPrice);
        listingMapper.insert(listing);
        return listing.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public long addImage(long sellerId, long listingId, ListingImageDTO request, String etag) {
        ListingDO listing = listingMapper.selectByIdForUpdate(listingId);
        requireOwner(listing, sellerId);
        requireEditable(listing);
        if (!IMAGE_TYPES.contains(request.contentType())) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "图片类型不合法");
        }

        ListingImageDO image = new ListingImageDO();
        // 图片 ID 由独立数据库序列分配，元数据仍随商品 JSON 快照整体更新。
        image.setId(listingMapper.nextImageId());
        image.setObjectKey(request.objectKey());
        image.setContentType(request.contentType());
        image.setSizeBytes(request.sizeBytes());
        image.setSortOrder(request.sortOrder());
        image.setEtag(etag);
        imageCodec.append(listing, image);
        listingMapper.updateById(listing);
        return image.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateDraft(long sellerId, long listingId, long expectedVersion,
                            CreateListingDTO request) {
        ListingDO listing = listingMapper.selectByIdForUpdate(listingId);
        requireOwner(listing, sellerId);
        requireEditable(listing);
        if (listing.getVersion() == null || listing.getVersion() != expectedVersion) {
            // If-Match 对应商品 version；过期页面不能覆盖另一设备刚保存的内容。
            throw new BusinessException(ResultCode.CONFLICT, "商品已在其他页面更新，请刷新后重试");
        }
        BigDecimal startPrice = parseMoney(request.startPrice());
        validateCodes(request.categoryCode(), request.conditionCode());
        if (startPrice.signum() <= 0) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "起拍价必须大于零");
        }
        BigDecimal seckillPrice = parseOptionalMoney(request.seckillPrice());
        BigDecimal shopPrice = parseOptionalMoney(request.shopPrice());
        validateDowngrade(request.autoDowngrade(), startPrice, seckillPrice, shopPrice);

        listing.setTitle(request.title().trim());
        listing.setDescription(request.description().trim());
        listing.setCategoryCode(request.categoryCode().trim());
        listing.setConditionCode(request.conditionCode().trim());
        listing.setDefects(request.defects());
        listing.setStartPrice(startPrice);
        listing.setDurationHours(request.durationHours());
        listing.setScheduledStart(request.scheduledStart());
        listing.setIsAutoDowngrade(request.autoDowngrade());
        listing.setSeckillPrice(seckillPrice);
        listing.setShopPrice(shopPrice);
        listing.setChatEnabled(request.chatEnabled());
        listing.setVersion(expectedVersion);
        if (listingMapper.updateById(listing) != 1) {
            throw new BusinessException(ResultCode.CONFLICT, "商品已在其他页面更新，请刷新后重试");
        }
    }

    @Transactional(readOnly = true)
    @Override
    public List<SellerListingImageVO> listImages(long sellerId, long listingId) {
        ListingDO listing = listingMapper.selectById(listingId);
        requireOwner(listing, sellerId);
        return imageCodec.read(listing).stream()
                .map(image -> {
                    var url = mediaStorageService.readUrl(sellerId, listingId, image.getId());
                    return new SellerListingImageVO(Long.toString(image.getId()),
                            image.getSortOrder() == null ? 0 : image.getSortOrder(),
                            image.getSizeBytes() == null ? 0L : image.getSizeBytes(),
                            image.getContentType(), url.url(), url.expiresAt());
                })
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public long submit(long sellerId, long listingId) {
        ListingDO listing = listingMapper.selectByIdForUpdate(listingId);
        requireOwner(listing, sellerId);
        if (listing.getStatus() != ListingStatus.DRAFT
                && listing.getStatus() != ListingStatus.CHANGES_REQUIRED) {
            throw new BusinessException(ResultCode.CONFLICT, "当前状态不能提交审核");
        }
        if (imageCodec.read(listing).isEmpty()) {
            // 图片是规则审核和 AI 风险判断的共同输入，没有图片不能进入审核队列。
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "至少需要一张商品图片");
        }
        UserDO seller = userMapper.selectById(sellerId);
        boolean manualRequired = seller.getCompletedReviewCount() < 3;

        long generation = listing.getReviewGeneration() == null
                ? 1 : listing.getReviewGeneration() + 1;
        markPendingReview(listing, generation, manualRequired);
        listingMapper.updateById(listing);

        long aiTaskId = aiTaskService.createReviewTask(listingId, generation);
        // 商品行、AI 任务和 Outbox 同事务提交；RabbitMQ 恢复后仍能重新投递审核事件。
        outboxService.append("listing.review-requested", "listing", listingId,
                Map.of("listingId", listingId, "aiTaskId", aiTaskId,
                        "reviewGeneration", generation));
        return listingId;
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<ListingVO> listVisible(String cursor, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        List<ListingDO> rows = safeList(listingMapper.selectVisiblePage(Cursor.parse(cursor), limit + 1));
        return page(rows, limit);
    }

    @Transactional(readOnly = true)
    @Override
    public ListingVO getVisible(long listingId) {
        ListingDO listing = listingMapper.selectById(listingId);
        if (listing == null || !PUBLIC_STATUSES.contains(listing.getStatus())) {
            // 公开详情使用正白名单，新增内部状态默认不会暴露。
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        return ListingVO.from(listing, mediaStorageService.coverUrl(listing));
    }

    @Transactional(readOnly = true)
    @Override
    public ListingVO getForSeller(long sellerId, long listingId) {
        ListingDO listing = listingMapper.selectById(listingId);
        requireOwner(listing, sellerId);
        return ListingVO.from(listing);
    }

    @Transactional(readOnly = true)
    @Override
    public AdminListingDetailVO getForReview(long listingId) {
        ListingDO listing = listingMapper.selectById(listingId);
        if (listing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        // 审核要核对提交时的完整描述和实拍图，因此不套用公开状态白名单；
        // 入口只有后台端点，越权风险由 AdminController 的方法级权限收敛。
        List<AdminListingImageVO> images = imageCodec.read(listing).stream()
                .map(image -> toReviewImage(listingId, image))
                .toList();
        return new AdminListingDetailVO(ListingVO.from(listing), images);
    }

    private AdminListingImageVO toReviewImage(long listingId, ListingImageDO image) {
        ImageUrlVO url = mediaStorageService.readUrlForReview(listingId, image.getId());
        return new AdminListingImageVO(image.getId().toString(), image.getContentType(),
                image.getSizeBytes(), image.getSortOrder(), url.url(), url.expiresAt());
    }

    @Transactional(readOnly = true)
    @Override
    public List<ListingVO> listMine(long sellerId) {
        return listingMapper.selectList(new LambdaQueryWrapper<ListingDO>()
                        .eq(ListingDO::getSellerId, sellerId)
                        .orderByDesc(ListingDO::getId)
                        .last("limit 20"))
                .stream().map(ListingVO::from).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<ListingVO> pageMine(long sellerId, String cursor, String status, int limit) {
        return personalWorkspaceService.sellerListings(sellerId, cursor, status, limit);
    }

    private ListingDO buildDraft(long sellerId, CreateListingDTO request, BigDecimal startPrice,
                                 BigDecimal seckillPrice, BigDecimal shopPrice) {
        ListingDO listing = new ListingDO();
        listing.setSellerId(sellerId);
        listing.setTitle(request.title().trim());
        listing.setDescription(request.description().trim());
        listing.setCategoryCode(request.categoryCode().trim());
        listing.setConditionCode(request.conditionCode().trim());
        listing.setDefects(request.defects());
        listing.setStartPrice(startPrice);
        listing.setDurationHours(request.durationHours());
        listing.setScheduledStart(request.scheduledStart());
        listing.setIsAutoDowngrade(request.autoDowngrade());
        listing.setSeckillPrice(seckillPrice);
        listing.setShopPrice(shopPrice);
        listing.setChatEnabled(request.chatEnabled());
        // 新商品只能进入拍卖草稿，客户端不能绕过审核直接创建秒杀或商城渠道。
        listing.setStatus(ListingStatus.DRAFT);
        return listing;
    }

    private void markPendingReview(ListingDO listing, long generation, boolean manualRequired) {
        listing.setStatus(ListingStatus.PENDING_REVIEW);
        listing.setReviewStatus(ReviewStatus.PENDING_AI);
        listing.setReviewGeneration(generation);
        listing.setManualRequired(manualRequired);
        listing.setRiskLevel(null);
        listing.setReviewEvidence(null);
        listing.setReviewDecision(null);
        listing.setReviewDecisionSource(null);
        listing.setReviewedBy(null);
        listing.setReviewedAt(null);
        listing.setRejectionReason(null);
    }

    private void requireEditable(ListingDO listing) {
        if (listing.getStatus() != ListingStatus.DRAFT
                && listing.getStatus() != ListingStatus.CHANGES_REQUIRED) {
            throw new BusinessException(ResultCode.CONFLICT, "当前状态不能修改图片");
        }
    }

    private void validateDowngrade(boolean enabled, BigDecimal startPrice,
                                   BigDecimal seckillPrice, BigDecimal shopPrice) {
        if (!enabled) {
            return;
        }
        // 自动降级必须一次授权完整价格链，避免流拍后系统猜测卖家底线。
        if (seckillPrice == null || shopPrice == null
                || shopPrice.signum() <= 0 || seckillPrice.signum() <= 0
                || shopPrice.compareTo(seckillPrice) > 0
                || seckillPrice.compareTo(startPrice) > 0) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT,
                    "自动降级价格必须满足 0 < 商城价 <= 秒杀价 <= 起拍价");
        }
    }

    private void validateCodes(String categoryCode, String conditionCode) {
        if (!CATEGORY_CODES.contains(categoryCode.trim())) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "商品类目编码不受支持");
        }
        if (!CONDITION_CODES.contains(conditionCode.trim())) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "商品成色编码不受支持");
        }
    }

    private BigDecimal parseMoney(String value) {
        try {
            return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "金额必须最多两位小数");
        }
    }

    private BigDecimal parseOptionalMoney(String value) {
        return value == null || value.isBlank() ? null : parseMoney(value);
    }

    private CursorPageVO<ListingVO> page(List<ListingDO> rows, int limit) {
        boolean hasMore = rows.size() > limit;
        List<ListingDO> selected = hasMore ? rows.subList(0, limit) : rows;
        String next = hasMore ? selected.getLast().getId().toString() : null;
        return new CursorPageVO<>(selected.stream()
                .map(row -> ListingVO.from(row, mediaStorageService.coverUrl(row)))
                .toList(), next);
    }

    private List<ListingDO> safeList(List<ListingDO> rows) {
        return rows == null ? List.of() : rows;
    }

    private void requireOwner(ListingDO listing, long sellerId) {
        if (listing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        if (!listing.getSellerId().equals(sellerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该商品");
        }
    }
}
