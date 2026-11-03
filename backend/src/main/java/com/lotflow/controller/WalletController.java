package com.lotflow.controller;

import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import com.lotflow.dto.WalletRechargeDTO;
import com.lotflow.security.CurrentUser;
import com.lotflow.service.WalletService;
import com.lotflow.vo.WalletTransactionVO;
import com.lotflow.vo.WalletVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 虚拟钱包查询与演示充值接口。 */
@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final CurrentUser currentUser;

    @GetMapping
    public WalletVO wallet() {
        return WalletVO.from(walletService.getWallet(currentUser.id()));
    }

    @GetMapping("/transactions")
    public List<WalletTransactionVO> transactions(@RequestParam(required = false) String cursor,
                                                  @RequestParam(defaultValue = "50") int limit) {
        return walletService.transactions(currentUser.id(), cursor, limit).stream()
                .map(WalletTransactionVO::from).toList();
    }

    @PostMapping("/recharges")
    public WalletVO recharge(@RequestHeader("Idempotency-Key") String idempotencyKey,
                             @Valid @RequestBody WalletRechargeDTO request) {
        // 是否开放充值由服务端环境配置决定，客户端不能自行切换到生产充值。
        walletService.recharge(currentUser.id(), parseMoney(request.amount()), idempotencyKey);
        return WalletVO.from(walletService.getWallet(currentUser.id()));
    }

    private BigDecimal parseMoney(String value) {
        try {
            return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "金额必须最多两位小数");
        }
    }
}
