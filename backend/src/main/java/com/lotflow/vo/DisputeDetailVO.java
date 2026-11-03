package com.lotflow.vo;

import java.util.List;

/** 争议详情与按时间排序的状态证据。 */
public record DisputeDetailVO(DisputeVO dispute, List<DisputeTimelineVO> timeline) {
}
