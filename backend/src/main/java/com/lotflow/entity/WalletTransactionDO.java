package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 不可变资金流水。 */
@Getter
@Setter
@TableName("wallet_transaction")
public class WalletTransactionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private String businessKey;
    private String transactionType;
    private BigDecimal availableChange;
    private BigDecimal frozenChange;
    private BigDecimal availableAfter;
    private BigDecimal frozenAfter;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime gmtCreate;
}
