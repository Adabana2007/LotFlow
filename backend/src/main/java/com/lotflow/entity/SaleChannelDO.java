package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lotflow.entity.enums.DomainEnums.ChannelDecisionStatus;
import com.lotflow.entity.enums.DomainEnums.ChannelDecisionValue;
import com.lotflow.entity.enums.DomainEnums.SaleChannelStatus;
import com.lotflow.entity.enums.DomainEnums.SaleChannelType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 单库存单元的渠道历史及流拍处置状态。 */
@Getter
@Setter
@TableName("sale_channel")
public class SaleChannelDO extends BaseDO {
    private Long listingId;
    private SaleChannelType channel;
    private SaleChannelStatus status;
    private BigDecimal price;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private Long orderId;
    private ChannelDecisionStatus decisionStatus;
    private ChannelDecisionValue decisionValue;
    private OffsetDateTime decisionExpiresAt;
}
