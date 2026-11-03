package com.lotflow.controller;

import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import com.lotflow.dto.DisputeSummaryRequestDTO;
import com.lotflow.dto.ListingDraftRequestDTO;
import com.lotflow.dto.SellerAnalysisRequestDTO;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.OrderDO;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.mapper.OrderMapper;
import com.lotflow.security.CurrentUser;
import com.lotflow.service.AiTaskService;
import com.lotflow.vo.AiTaskVO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** AI 异步任务、只读助手和经营建议入口。 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiTaskService taskService;
    private final ListingMapper listingMapper;
    private final OrderMapper orderMapper;
    private final CurrentUser currentUser;

    public AiController(AiTaskService taskService, ListingMapper listingMapper,
                        OrderMapper orderMapper, CurrentUser currentUser) {
        this.taskService = taskService;
        this.listingMapper = listingMapper;
        this.orderMapper = orderMapper;
        this.currentUser = currentUser;
    }

    @PostMapping("/listing-drafts")
    public ResponseEntity<Map<String, String>> listingDraft(
            @Valid @RequestBody ListingDraftRequestDTO request) {
        long id = taskService.submitListingDraft(currentUser.id(), request);
        // 发布草稿依赖模型且可能较慢，接口只返回 202 和 taskId，客户端轮询任务状态。
        return ResponseEntity.accepted().body(Map.of("taskId", Long.toString(id)));
    }

    @GetMapping("/tasks/{taskId}")
    public AiTaskVO task(@PathVariable long taskId) {
        // 普通用户只能查看自己的 AI 任务，管理员可通过统一任务查询接口排查失败。
        return taskService.get(currentUser.id(), currentUser.hasRole("ADMIN"), taskId);
    }

    @PostMapping("/seller-analyses")
    public ResponseEntity<Map<String, String>> sellerAnalysis(
            @Valid @RequestBody SellerAnalysisRequestDTO request) {
        ListingDO listing = listingMapper.selectById(request.listingId());
        if (listing == null || !listing.getSellerId().equals(currentUser.id())) {
            // 经营建议可能包含价格和流量信息，入口先校验商品归属，避免分析其他卖家的商品。
            throw new BusinessException(ResultCode.FORBIDDEN, "无权分析该商品");
        }
        long id = taskService.submitSellerAnalysis(currentUser.id(), request);
        return ResponseEntity.accepted().body(Map.of("taskId", Long.toString(id)));
    }

    @PostMapping("/dispute-summaries")
    public ResponseEntity<Map<String, String>> disputeSummary(
            @Valid @RequestBody DisputeSummaryRequestDTO request) {
        OrderDO order = orderMapper.selectById(request.orderId());
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!currentUser.hasRole("ADMIN") && !order.getBuyerId().equals(currentUser.id())
                && !order.getSellerId().equals(currentUser.id())) {
            // 争议摘要只向订单买卖双方或管理员开放，避免聊天和证据信息跨订单泄露。
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该争议");
        }
        long id = taskService.submitDisputeSummary(currentUser.id(), request.orderId());
        return ResponseEntity.accepted().body(Map.of("taskId", Long.toString(id)));
    }
}
