package com.lotflow.service.impl;

import com.lotflow.service.AiResultService;
import com.lotflow.service.MediaStorageService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotflow.ai.chat.ChatRiskAiResult;
import com.lotflow.ai.gateway.AiGateway;
import com.lotflow.ai.listing.ListingDraftAiResult;
import com.lotflow.ai.review.ReviewRiskAiResult;
import com.lotflow.ai.seller.SellerAnalysisAiResult;
import com.lotflow.dto.ListingDraftRequestDTO;
import com.lotflow.dto.SellerAnalysisRequestDTO;
import com.lotflow.entity.AuctionDO;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.MessageDO;
import com.lotflow.entity.OrderDO;
import com.lotflow.mapper.AuctionMapper;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.mapper.MessageMapper;
import com.lotflow.mapper.OrderMapper;
import com.lotflow.entity.ListingImageDO;
import com.lotflow.ai.review.ReviewPolicy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Set;
import java.util.List;

/** AI 异步任务执行器。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiWorker {

    private static final String PROMPT_VERSION = "v1";

    private final AiGateway gateway;
    private final AiResultService resultService;
    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final ListingMapper listingMapper;
    private final MessageMapper messageMapper;
    private final OrderMapper orderMapper;
    private final AuctionMapper auctionMapper;
    private final ListingImageJsonCodec imageCodec;
    private final MediaStorageService mediaStorageService;
    private final ReviewPolicy reviewPolicy;

    public void listingDraft(long taskId, ListingDraftRequestDTO request) {
        try {
            // 任务状态先置 RUNNING 再调用模型，运营后台能区分排队、执行和最终失败。
            resultService.markRunning(taskId);
            String input = toJson(Map.of("description", request.sellerDescription(),
                    "imageKeys", request.imageKeys() == null ? java.util.List.of() : request.imageKeys()));
            var call = gateway.structured("prompts/v1/listing-draft.st", input,
                    ListingDraftAiResult.class);
            validate(call.value());
            resultService.completeListingDraft(taskId, call.value(), call, PROMPT_VERSION);
        } catch (RuntimeException exception) {
            fail(taskId, exception);
        }
    }

    public void review(long taskId, long listingId, long reviewGeneration) {
        try {
            resultService.markRunning(taskId);
            ListingDO listing = listingMapper.selectById(listingId);
            if (listing == null || listing.getReviewGeneration() == null
                    || listing.getReviewGeneration() != reviewGeneration) {
                // 旧 generation 的延迟消息直接结束，不能对新提交内容产生风险标签。
                resultService.fail(taskId, "REVIEW_GENERATION_STALE");
                return;
            }
            String input = toJson(Map.of(
                    "title", listing.getTitle(),
                    "description", listing.getDescription(),
                    "category", listing.getCategoryCode(),
                    "condition", listing.getConditionCode(),
                    "defects", listing.getDefects() == null ? "" : listing.getDefects()));
            List<ListingImageDO> images = imageCodec.read(listing);
            var media = mediaStorageService.isPublicEndpointReachableByModel()
                    ? images.stream().map(mediaStorageService::mediaFor).toList()
                    : List.<org.springframework.ai.content.Media>of();
            var call = gateway.structuredWithMedia("prompts/v1/review-risk.st", input,
                    media, ReviewRiskAiResult.class);
            validate(call.value());
            // AI 只提供风险标签和证据；只有图片完整、无命中规则且 ReviewPolicy 通过时才具备自动审核资格。
            boolean policyEligible = (call.value().matchedRules() == null
                    || call.value().matchedRules().isEmpty())
                    && media.size() == images.size()
                    && reviewPolicy.allowsAutoApproval(listing, images.size());
            resultService.completeReview(taskId, listingId, reviewGeneration, call.value(), call,
                    PROMPT_VERSION, policyEligible);
        } catch (RuntimeException exception) {
            fail(taskId, exception);
        }
    }

    public void chatRisk(long taskId, long messageId) {
        try {
            resultService.markRunning(taskId);
            MessageDO message = messageMapper.selectById(messageId);
            var call = gateway.structured("prompts/v1/chat-risk.st", message.getContent(),
                    ChatRiskAiResult.class);
            validate(call.value());
            // 聊天风险只写入风险标签和人工复核标记，AI 不会自动封号、删除消息或阻断交易。
            resultService.completeChatRisk(taskId, messageId, call.value(), call, PROMPT_VERSION);
        } catch (RuntimeException exception) {
            fail(taskId, exception);
        }
    }

    public void sellerAnalysis(long taskId, SellerAnalysisRequestDTO request) {
        try {
            resultService.markRunning(taskId);
            ListingDO listing = listingMapper.selectById(request.listingId());
            Long orderCount = orderMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderDO>()
                            .eq(OrderDO::getListingId, listing.getId()));
            AuctionDO auction = auctionMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuctionDO>()
                            .eq(AuctionDO::getListingId, listing.getId())
                            .orderByDesc(AuctionDO::getId)
                            .last("limit 1"));
            String input = toJson(Map.of("listingTitle", listing.getTitle(),
                    "status", listing.getStatus().name(), "orderCount", orderCount,
                    "startPrice", listing.getStartPrice(),
                    "auctionCurrentPrice", auction == null ? "" : auction.getCurrentPrice()));
            var call = gateway.structured("prompts/v1/seller-analysis.st", input,
                    SellerAnalysisAiResult.class);
            validate(call.value());
            // 商家经营任务只生成建议，不直接改价、重新上架或发送买家消息。
            resultService.completeGeneric(taskId, call.value(), call, PROMPT_VERSION);
        } catch (RuntimeException exception) {
            fail(taskId, exception);
        }
    }

    public void disputeSummary(long taskId, long orderId) {
        try {
            resultService.markRunning(taskId);
            OrderDO order = orderMapper.selectById(orderId);
            String input = toJson(Map.of("orderStatus", order.getStatus().name(),
                    "reason", order.getDisputeReason() == null ? "" : order.getDisputeReason(),
                    "evidence", order.getDisputeEvidence() == null ? "" : order.getDisputeEvidence(),
                    "shippedAt", String.valueOf(order.getShippedAt())));
            var call = gateway.structured("prompts/v1/dispute-summary.st", input,
                    com.lotflow.ai.risk.DisputeSummaryAiResult.class);
            validate(call.value());
            // 争议摘要只整理证据和时间线，退款、赔付和争议结论仍由运营人员决定。
            resultService.completeGeneric(taskId, call.value(), call, PROMPT_VERSION);
        } catch (RuntimeException exception) {
            fail(taskId, exception);
        }
    }

    private <T> void validate(T value) {
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            // 模型输出必须先通过 Bean Validation，非法结构不能进入业务状态转换。
            throw new IllegalArgumentException("AI structured output validation failed");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("cannot serialize AI input", exception);
        }
    }

    private void fail(long taskId, RuntimeException exception) {
        // 任务失败单独提交 FALLBACK，再向消费者抛出，让 RabbitMQ 按统一策略重试并最终进入死信队列。
        log.warn("ai task failed, taskId={}, reason={}", taskId, exception.getClass().getSimpleName());
        resultService.fail(taskId, exception.getClass().getSimpleName());
        throw exception;
    }
}
