package com.lotflow.controller;

import com.lotflow.dto.ChannelDecisionDTO;
import com.lotflow.entity.SaleChannelDO;
import com.lotflow.vo.SaleChannelVO;
import com.lotflow.security.CurrentUser;
import com.lotflow.service.ChannelService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 流拍后卖家降级决定和渠道查询。 */
@RestController
@RequestMapping("/api/v1/listings")
public class ChannelController {

    private final ChannelService channelService;
    private final CurrentUser currentUser;

    public ChannelController(ChannelService channelService, CurrentUser currentUser) {
        this.channelService = channelService;
        this.currentUser = currentUser;
    }

    @PostMapping("/{listingId}/unsold-decisions")
    public ResponseEntity<Void> decide(@PathVariable long listingId,
                                       @Valid @RequestBody ChannelDecisionDTO request) {
        // 流拍决定属于卖家授权动作；Service 校验 sellerId 和决策窗口后才切换销售渠道。
        channelService.decide(currentUser.id(), listingId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{listingId}/channels")
    public List<SaleChannelVO> channels(@PathVariable long listingId) {
        // 渠道历史包含拍卖、秒杀和商城的价格与有效期，只对商品卖家开放。
        return channelService.listChannels(currentUser.id(), listingId);
    }

    @PostMapping("/{listingId}/delistings")
    public ResponseEntity<Void> delist(@PathVariable long listingId) {
        // 下架是商城在售渠道的一次状态转换：终止渠道但不删除商品和订单历史。
        channelService.delistShop(currentUser.id(), listingId);
        return ResponseEntity.noContent().build();
    }
}
