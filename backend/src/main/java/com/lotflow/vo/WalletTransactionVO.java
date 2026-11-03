package com.lotflow.vo;

import com.lotflow.entity.WalletTransactionDO;

import java.time.OffsetDateTime;

/** 资金流水响应。 */
public record WalletTransactionVO(String id, String businessKey, String transactionType,
                                  String availableChange, String frozenChange,
                                  String availableAfter, String frozenAfter,
                                  OffsetDateTime createdAt) {
    public static WalletTransactionVO from(WalletTransactionDO transaction) {
        return new WalletTransactionVO(transaction.getId().toString(), transaction.getBusinessKey(),
                transaction.getTransactionType(), transaction.getAvailableChange().toPlainString(),
                transaction.getFrozenChange().toPlainString(),
                transaction.getAvailableAfter().toPlainString(),
                transaction.getFrozenAfter().toPlainString(), transaction.getGmtCreate());
    }
}
