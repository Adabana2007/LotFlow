package com.lotflow.service;

import com.lotflow.entity.WalletAccountDO;
import com.lotflow.entity.WalletTransactionDO;

import java.math.BigDecimal;
import java.util.List;

/** 钱包余额和不可变资金流水。 */
public interface WalletService {

    String ESCROW_ACCOUNT = "PLATFORM_ESCROW";
    String REVENUE_ACCOUNT = "PLATFORM_REVENUE";
    String RECHARGE_ACCOUNT = "PLATFORM_RECHARGE";

    long createUserAccount(long userId);

    WalletAccountDO getWallet(long userId);

    void recharge(long userId, BigDecimal amount, String businessKey);

    List<WalletTransactionDO> transactions(long userId, String cursor, int requestedLimit);

    void freeze(long userId, BigDecimal amount, String businessKey);

    void releaseFrozen(long userId, BigDecimal amount, String businessKey);

    void moveFrozenToEscrow(long userId, BigDecimal amount, String businessKey);

    void moveAvailableToEscrow(long userId, BigDecimal amount, String businessKey);

    void transferFrozenToUser(long fromUserId, long toUserId, BigDecimal amount, String businessKey);

    void transferEscrowToAvailable(long toUserId, BigDecimal amount, String businessKey);

    static String userAccountCode(long userId) {
        return "USER:" + userId;
    }
}
