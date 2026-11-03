package com.lotflow.vo;

import com.lotflow.entity.WalletAccountDO;

/** 可用余额与冻结余额。 */
public record WalletVO(String accountId, String availableBalance, String frozenBalance) {
    public static WalletVO from(WalletAccountDO account) {
        return new WalletVO(account.getId().toString(), account.getAvailableBalance().toPlainString(),
                account.getFrozenBalance().toPlainString());
    }
}
