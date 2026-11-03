package com.lotflow.vo;

import java.time.LocalDate;

/** 按日汇总的已完成订单数量与金额。 */
public record DailyTradeVO(LocalDate day, long completedOrders, String completedAmount) {
}
