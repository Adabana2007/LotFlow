package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;

/** 虚拟钱包充值。 */
public record WalletRechargeDTO(@NotBlank String amount) {
}
