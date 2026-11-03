package com.lotflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 虚拟钱包演示充值开关。 */
@ConfigurationProperties("lotflow.wallet")
public record WalletProperties(boolean rechargeEnabled) {
}
