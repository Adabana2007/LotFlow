package com.lotflow.controller;

import com.lotflow.dto.DisputeDTO;
import com.lotflow.dto.ShipmentDTO;
import com.lotflow.security.CurrentUser;
import com.lotflow.service.OrderService;
import com.lotflow.service.PersonalWorkspaceService;
import com.lotflow.vo.OrderSummaryVO;
import com.lotflow.vo.OrderVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

/** 订单命令接口，状态变化使用明确子资源。 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final PersonalWorkspaceService personalWorkspaceService;
    private final CurrentUser currentUser;

    public OrderController(OrderService orderService,
                           PersonalWorkspaceService personalWorkspaceService,
                           CurrentUser currentUser) {
        this.orderService = orderService;
        this.personalWorkspaceService = personalWorkspaceService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public com.lotflow.common.CursorPageVO<OrderSummaryVO> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "ALL") String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit) {
        return personalWorkspaceService.orders(currentUser.id(), cursor, role, status, limit);
    }

    @GetMapping("/{orderId}")
    public OrderVO get(@PathVariable long orderId) {
        return orderService.get(currentUser.id(), currentUser.hasRole("ADMIN"), orderId);
    }

    @PostMapping("/{orderId}/payments")
    public OrderVO pay(@PathVariable long orderId,
                       @RequestHeader("Idempotency-Key") String idempotencyKey) {
        // 付款会移动冻结和可用资金，必须携带幂等键，重试只能返回首次付款结果。
        return orderService.pay(currentUser.id(), orderId, idempotencyKey);
    }

    @PostMapping("/{orderId}/shipments")
    public OrderVO ship(@PathVariable long orderId, @Valid @RequestBody ShipmentDTO request) {
        // 发货是 PAID 到 SHIPPED 的明确状态转换，同时写入物流信息并开启确认收货倒计时。
        return orderService.ship(currentUser.id(), orderId, request);
    }

    @PostMapping("/{orderId}/receipt-confirmations")
    public OrderVO confirm(@PathVariable long orderId) {
        // 确认收货只进入售后争议期，担保货款仍由争议期结束任务负责结算。
        return orderService.confirmReceipt(currentUser.id(), orderId);
    }

    @PostMapping("/{orderId}/disputes")
    public OrderVO dispute(@PathVariable long orderId, @Valid @RequestBody DisputeDTO request) {
        // 争议必须由买家在七天内发起，订单转为 DISPUTED 后暂停资金结算。
        return orderService.openDispute(currentUser.id(), orderId, request);
    }

    @PostMapping("/{orderId}/second-chance-offers")
    public OrderVO secondChance(@PathVariable long orderId) {
        // 卖家只能对已违约订单发起第二高顺延；创建资格本身不冻结第二高买家的资金。
        return orderService.offerSecondChance(currentUser.id(), orderId);
    }
}
