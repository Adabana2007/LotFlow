package com.lotflow.vo;

/** 钱包当前余额与最后一笔不可变流水的逐账户核对结果。 */
public record WalletReconciliationVO(
        String accountId,
        String accountCode,
        String ownerType,
        String ownerId,
        String availableBalance,
        String frozenBalance,
        String ledgerAvailableBalance,
        String ledgerFrozenBalance,
        String difference,
        boolean consistent
) {
}
