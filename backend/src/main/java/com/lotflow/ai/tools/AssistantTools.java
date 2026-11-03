package com.lotflow.ai.tools;

import com.lotflow.entity.WalletAccountDO;
import com.lotflow.service.ListingService;
import com.lotflow.service.OrderService;
import com.lotflow.service.WalletService;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.Map;

/** 平台助手只读工具。 */
public class AssistantTools {

    private final long userId;
    private final ListingService listingService;
    private final OrderService orderService;
    private final WalletService walletService;

    public AssistantTools(long userId, ListingService listingService,
                          OrderService orderService, WalletService walletService) {
        // userId 由服务端安全上下文注入，模型只能选择工具，不能指定查询哪个用户。
        this.userId = userId;
        this.listingService = listingService;
        this.orderService = orderService;
        this.walletService = walletService;
    }

    @Tool(description = "查询当前登录用户自己的商品列表")
    public List<Map<String, String>> myListings() {
        return listingService.listMine(userId).stream().map(item -> Map.of(
                "id", item.id(), "title", item.title(), "status", item.status())).toList();
    }

    @Tool(description = "查询当前登录用户自己的订单")
    public List<Map<String, String>> myOrders() {
        return orderService.listMine(userId).stream().map(item -> Map.of(
                "id", item.id(), "orderNo", item.orderNo(), "status", item.status())).toList();
    }

    @Tool(description = "查询当前登录用户自己的钱包余额")
    public Map<String, String> myWallet() {
        WalletAccountDO wallet = walletService.getWallet(userId);
        return Map.of("available", wallet.getAvailableBalance().toPlainString(),
                "frozen", wallet.getFrozenBalance().toPlainString());
    }

}
