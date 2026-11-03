package com.lotflow.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 拍卖最低加价和结束时间顺延规则。 */
public final class AuctionPolicy {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");
    private static final int EXTENSION_SECONDS = 120;
    private static final int MAX_EXTENSION_SECONDS = 1800;

    private AuctionPolicy() {
    }

    public static BigDecimal minimumIncrement(BigDecimal currentPrice) {
        // 加价幅度按当前价分档：低于 100 元至少 5 元，1000 元以下 10 元，1 万元以下 50 元，其余 100 元。
        if (currentPrice.compareTo(HUNDRED) < 0) {
            return new BigDecimal("5.00");
        }
        if (currentPrice.compareTo(THOUSAND) < 0) {
            return new BigDecimal("10.00");
        }
        if (currentPrice.compareTo(TEN_THOUSAND) < 0) {
            return new BigDecimal("50.00");
        }
        return new BigDecimal("100.00");
    }

    /** 只有在最后五分钟内的有效出价才顺延，累计顺延严格不超过三十分钟。 */
    public static Extension extension(OffsetDateTime endTime, int extensionSeconds,
                                      OffsetDateTime now) {
        // 只有结束前五分钟内的有效出价才触发顺延，且累计顺延不能超过三十分钟上限。
        if (now.isBefore(endTime.minusMinutes(5))) {
            return new Extension(endTime, extensionSeconds);
        }
        int remaining = MAX_EXTENSION_SECONDS - extensionSeconds;
        // 最后一次顺延只补足剩余上限，保证累计秒数不会超过平台规则。
        int delta = Math.max(0, Math.min(EXTENSION_SECONDS, remaining));
        return new Extension(endTime.plusSeconds(delta), extensionSeconds + delta);
    }

    public record Extension(OffsetDateTime endTime, int extensionSeconds) {
    }
}
