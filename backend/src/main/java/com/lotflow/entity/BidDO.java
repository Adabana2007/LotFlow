package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lotflow.entity.enums.DomainEnums.BidStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 拍卖出价记录。 */
@Getter
@Setter
@TableName("bid")
public class BidDO extends BaseDO {
    private Long auctionId;
    private Long bidderId;
    private BigDecimal amount;
    private BigDecimal depositRequired;
    private String idempotencyKey;
    private BidStatus status;
}
