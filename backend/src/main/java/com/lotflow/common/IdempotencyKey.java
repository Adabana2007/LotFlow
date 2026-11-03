package com.lotflow.common;

/** 出价、下单、支付和资金命令的统一幂等键校验。 */
public final class IdempotencyKey {

    private IdempotencyKey() {
    }

    public static String require(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "缺少有效的 Idempotency-Key");
        }
        return value.trim();
    }
}
