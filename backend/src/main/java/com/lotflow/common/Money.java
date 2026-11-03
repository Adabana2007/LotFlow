package com.lotflow.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 平台金额和保证金规则。 */
public final class Money {

    public static final BigDecimal BUYER_DEPOSIT_RATE = new BigDecimal("0.05");
    public static final BigDecimal SELLER_DEPOSIT_RATE = new BigDecimal("0.01");
    public static final BigDecimal MIN_BUYER_DEPOSIT = new BigDecimal("20.00");
    public static final BigDecimal MAX_BUYER_DEPOSIT = new BigDecimal("2000.00");
    public static final BigDecimal MIN_SELLER_DEPOSIT = new BigDecimal("10.00");
    public static final BigDecimal MAX_SELLER_DEPOSIT = new BigDecimal("500.00");
    public static final BigDecimal ONE = new BigDecimal("1.00");

    private Money() {
    }

    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal buyerDeposit(BigDecimal bidAmount) {
        // 买家保证金为出价 5%，向上取整到元后限制在 20 至 2000 元，统一由领域方法计算。
        BigDecimal rounded = roundUpToOne(bidAmount.multiply(BUYER_DEPOSIT_RATE));
        return rounded.max(MIN_BUYER_DEPOSIT).min(MAX_BUYER_DEPOSIT).setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal sellerDeposit(BigDecimal startPrice) {
        // 卖家保证金为起拍价 1%，向上取整到元后限制在 10 至 500 元，避免各 Service 重复实现。
        BigDecimal rounded = roundUpToOne(startPrice.multiply(SELLER_DEPOSIT_RATE));
        return rounded.max(MIN_SELLER_DEPOSIT).min(MAX_SELLER_DEPOSIT).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** 保证金展示金额必须是整元，向上取整可避免冻结金额小于应担保额度。 */
    public static BigDecimal roundUpToOne(BigDecimal value) {
        return value.setScale(0, RoundingMode.CEILING).setScale(2, RoundingMode.UNNECESSARY);
    }
}
