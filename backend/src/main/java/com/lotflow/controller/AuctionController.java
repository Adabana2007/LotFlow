package com.lotflow.controller;

import com.lotflow.common.CursorPageVO;
import com.lotflow.dto.PlaceBidDTO;
import com.lotflow.security.CurrentUser;
import com.lotflow.service.AuctionService;
import com.lotflow.service.PersonalWorkspaceService;
import com.lotflow.vo.AuctionVO;
import com.lotflow.vo.BidHistoryVO;
import com.lotflow.vo.BidVO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 拍卖查询、出价和取消接口。 */
@RestController
@RequestMapping("/api/v1/auctions")
public class AuctionController {

    private final AuctionService auctionService;
    private final PersonalWorkspaceService personalWorkspaceService;
    private final CurrentUser currentUser;

    public AuctionController(AuctionService auctionService,
                             PersonalWorkspaceService personalWorkspaceService,
                             CurrentUser currentUser) {
        this.auctionService = auctionService;
        this.personalWorkspaceService = personalWorkspaceService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public CursorPageVO<AuctionVO> list(@RequestParam(required = false) String cursor,
                                        @RequestParam(defaultValue = "20") int limit,
                                        @RequestParam(defaultValue = "false") boolean activeOnly) {
        // 默认返回全部场次：商品详情要靠历史场次展示成交价，只有拍卖大厅才需要只看进行中的场次。
        return auctionService.list(cursor, limit, activeOnly);
    }

    @GetMapping("/mine/bids")
    public CursorPageVO<BidHistoryVO> myBids(@RequestParam(required = false) String cursor,
                                             @RequestParam(defaultValue = "50") int limit) {
        return personalWorkspaceService.bidHistory(currentUser.id(), cursor, limit);
    }

    @GetMapping("/{auctionId}")
    public AuctionVO get(@PathVariable long auctionId) {
        return auctionService.get(auctionId);
    }

    @GetMapping("/{auctionId}/bids")
    public CursorPageVO<BidVO> bids(@PathVariable long auctionId,
                                    @RequestParam(required = false) String cursor,
                                    @RequestParam(defaultValue = "50") int limit) {
        return auctionService.bids(auctionId, cursor, limit);
    }

    @PostMapping("/{auctionId}/bids")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BidVO> bid(@PathVariable long auctionId,
                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @Valid @RequestBody PlaceBidDTO request) {
        // 出价是新资源且必须支持重试；Idempotency-Key 防止重复冻结保证金和重复抬高最高价。
        return ResponseEntity.status(201)
                .body(auctionService.placeBid(currentUser.id(), auctionId, request, idempotencyKey));
    }

    @DeleteMapping("/{auctionId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> cancel(@PathVariable long auctionId) {
        // 删除接口表达卖家取消拍卖；是否允许取消以及保证金如何赔付由 Service 按当前状态决定。
        auctionService.cancel(currentUser.id(), auctionId);
        return ResponseEntity.noContent().build();
    }
}
