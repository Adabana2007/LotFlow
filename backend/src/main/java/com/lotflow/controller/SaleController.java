package com.lotflow.controller;

import com.lotflow.security.CurrentUser;
import com.lotflow.service.SeckillService;
import com.lotflow.service.ShopService;
import com.lotflow.vo.OrderVO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 秒杀与商城的明确下单子资源。 */
@RestController
@RequestMapping("/api/v1")
public class SaleController {

    private final SeckillService seckillService;
    private final ShopService shopService;
    private final CurrentUser currentUser;

    public SaleController(SeckillService seckillService, ShopService shopService,
                          CurrentUser currentUser) {
        this.seckillService = seckillService;
        this.shopService = shopService;
        this.currentUser = currentUser;
    }

    @PostMapping("/seckill/{listingId}/orders")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<OrderVO> buySeckill(@PathVariable long listingId,
                                              @RequestHeader("Idempotency-Key") String key) {
        // 秒杀下单先经过 Redis 库存闸门再创建订单；幂等键防止用户重试时重复占用同一库存。
        return ResponseEntity.status(201)
                .body(seckillService.purchase(currentUser.id(), listingId, key));
    }

    @PostMapping("/shop/{listingId}/orders")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<OrderVO> buyShop(@PathVariable long listingId,
                                           @RequestHeader("Idempotency-Key") String key) {
        // 商城一库存只生成一个有效订单，渠道行锁和幂等键共同保证重复请求不会重复成交。
        return ResponseEntity.status(201)
                .body(shopService.purchase(currentUser.id(), listingId, key));
    }
}
