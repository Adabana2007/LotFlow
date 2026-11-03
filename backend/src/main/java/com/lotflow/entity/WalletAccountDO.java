package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 用户或平台钱包账户。 */
@Getter
@Setter
@TableName("wallet_account")
public class WalletAccountDO extends BaseDO {
    private String accountCode;
    private String ownerType;
    private Long ownerId;
    private BigDecimal availableBalance;
    private BigDecimal frozenBalance;
}
