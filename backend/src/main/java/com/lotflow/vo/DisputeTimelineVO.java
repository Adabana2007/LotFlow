package com.lotflow.vo;

import java.time.OffsetDateTime;

/** 由订单事实生成的可核对争议时间线节点。 */
public record DisputeTimelineVO(String type, String title, String detail,
                                OffsetDateTime occurredAt) {
}
