package com.lotflow.dto;

import com.lotflow.entity.enums.DomainEnums.ChannelDecisionValue;
import jakarta.validation.constraints.NotNull;

/** 未授权流拍后卖家选择。 */
public record ChannelDecisionDTO(@NotNull ChannelDecisionValue decision, String seckillPrice) {
}
