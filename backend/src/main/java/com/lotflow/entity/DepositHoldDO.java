package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lotflow.entity.enums.DomainEnums.DepositStatus;
import com.lotflow.entity.enums.DomainEnums.DepositType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 按拍卖独立维护的保证金冻结记录。 */
@Getter
@Setter
@TableName("deposit_hold")
public class DepositHoldDO extends BaseDO {
    private Long auctionId;
    private Long userId;
    private DepositType depositType;
    private BigDecimal amount;
    private DepositStatus status;
    private String businessKey;
}
