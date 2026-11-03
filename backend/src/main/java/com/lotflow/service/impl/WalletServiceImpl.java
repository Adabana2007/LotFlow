package com.lotflow.service.impl;

import com.lotflow.service.OperationMetricsService;
import com.lotflow.service.WalletService;

import com.lotflow.common.BusinessException;
import com.lotflow.common.Cursor;
import com.lotflow.common.Money;
import com.lotflow.common.ResultCode;
import com.lotflow.entity.WalletAccountDO;
import com.lotflow.entity.WalletTransactionDO;
import com.lotflow.mapper.WalletAccountMapper;
import com.lotflow.mapper.WalletTransactionMapper;
import com.lotflow.config.WalletProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** 钱包余额只能通过原子条件更新改变，所有变化同时写不可变流水。 */
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletAccountMapper accountMapper;
    private final WalletTransactionMapper transactionMapper;
    private final WalletProperties walletProperties;
    private final OperationMetricsService metrics;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public long createUserAccount(long userId) {
        String code = userAccountCode(userId);
        // 使用数据库冲突忽略收敛并发开户，避免唯一键异常把当前 PostgreSQL 事务置为 aborted。
        accountMapper.insertUserAccountIfAbsent(code, userId);
        WalletAccountDO account = accountMapper.selectByCodeForUpdate(code);
        if (account == null) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "钱包账户创建失败");
        }
        return account.getId();
    }

    @Transactional(readOnly = true)
    @Override
    public WalletAccountDO getWallet(long userId) {
        WalletAccountDO account = accountMapper.selectByCode(userAccountCode(userId));
        if (account == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "钱包不存在");
        }
        return account;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void recharge(long userId, BigDecimal amount, String businessKey) {
        Money.scale(amount);
        // 充值只用于本地演示，金额必须大于零并由服务端配置开关控制，不能由客户端自行开启。
        if (amount.signum() <= 0) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "充值金额必须大于零");
        }
        if (!walletProperties.rechargeEnabled()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前环境不开放充值");
        }
        if (amount.compareTo(new BigDecimal("10000.00")) > 0) {
            // 单笔一万、账户十万是演示钱包风控上限，必须在入账前按可用加冻结总额判断。
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "单笔充值不能超过 10000 元");
        }
        WalletAccountDO user = accountMapper.selectByCodeForUpdate(userAccountCode(userId));
        if (alreadyRecorded(user.getId(), businessKey)) {
            // 幂等键已经存在时直接成功返回，重复充值不能再增加余额或写第二条流水。
            return;
        }
        BigDecimal totalAfter = user.getAvailableBalance().add(user.getFrozenBalance()).add(amount);
        if (totalAfter.compareTo(new BigDecimal("100000.00")) > 0) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "钱包余额不能超过 100000 元");
        }
        accountMapper.addAvailable(user.getId(), amount);
        record(user.getId(), businessKey, "RECHARGE", amount, BigDecimal.ZERO, user.getId());
    }

    @Transactional(readOnly = true)
    @Override
    public List<WalletTransactionDO> transactions(long userId, String cursor, int requestedLimit) {
        WalletAccountDO account = accountMapper.selectByCode(userAccountCode(userId));
        if (account == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "钱包不存在");
        }
        Long cursorId = Cursor.parse(cursor);
        // 资金流水使用 ID 游标最多返回 100 条，避免用户长期交易后 offset 分页变慢或跳过记录。
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return transactionMapper.selectPage(account.getId(), cursorId, limit);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void freeze(long userId, BigDecimal amount, String businessKey) {
        // 零金额冻结不产生资金变化，直接返回可避免生成无业务意义的流水。
        if (amount.signum() < 0) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "冻结金额不能为负数");
        }
        if (amount.signum() == 0) {
            return;
        }
        WalletAccountDO account = accountMapper.selectByCodeForUpdate(userAccountCode(userId));
        if (alreadyRecorded(account.getId(), businessKey)) {
            return;
        }
        // 可用余额检查和冻结增加放在同一条 SQL 中；影响行数为零说明余额不足，不能继续写成功流水。
        int affected = accountMapper.freeze(account.getId(), amount);
        if (affected == 0) {
            metrics.recordWalletFailure("freeze");
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "钱包可用余额不足");
        }
        record(account.getId(), businessKey, "FREEZE", amount.negate(), amount, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void releaseFrozen(long userId, BigDecimal amount, String businessKey) {
        if (amount.signum() == 0) {
            return;
        }
        // 解冻只减少冻结余额并增加可用余额；条件更新失败说明冻结事实与调用方预期不一致。
        WalletAccountDO account = accountMapper.selectByCodeForUpdate(userAccountCode(userId));
        if (alreadyRecorded(account.getId(), businessKey)) {
            return;
        }
        int affected = accountMapper.releaseFrozen(account.getId(), amount);
        if (affected == 0) {
            metrics.recordWalletFailure("release");
            throw new BusinessException(ResultCode.CONFLICT, "冻结保证金状态异常");
        }
        record(account.getId(), businessKey, "UNFREEZE", amount, amount.negate(), userId);
    }

    /** 支付时冻结保证金直接进入担保账户，避免先解冻后又被其他订单消费。 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void moveFrozenToEscrow(long userId, BigDecimal amount, String businessKey) {
        if (amount.signum() == 0) {
            return;
        }
        List<WalletAccountDO> locked = lockUserAndEscrow(userId);
        WalletAccountDO user = locked.get(0);
        WalletAccountDO escrow = locked.get(1);
        if (alreadyRecorded(user.getId(), businessKey + ":source")) {
            return;
        }
        // 先消耗用户冻结额，再增加担保账户余额，两步必须在同一事务内保持总额守恒。
        requireAffected(accountMapper.consumeFrozen(user.getId(), amount),
                "冻结余额不足", "move-frozen-to-escrow");
        accountMapper.addAvailable(escrow.getId(), amount);
        record(user.getId(), businessKey + ":source", "ESCROW_FROM_FROZEN",
                BigDecimal.ZERO, amount.negate(), userId);
        record(escrow.getId(), businessKey + ":escrow", "ESCROW_RECEIVE",
                amount, BigDecimal.ZERO, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void moveAvailableToEscrow(long userId, BigDecimal amount, String businessKey) {
        if (amount.signum() == 0) {
            return;
        }
        List<WalletAccountDO> locked = lockUserAndEscrow(userId);
        WalletAccountDO user = locked.get(0);
        WalletAccountDO escrow = locked.get(1);
        if (alreadyRecorded(user.getId(), businessKey + ":source")) {
            return;
        }
        requireAffected(accountMapper.spendAvailable(user.getId(), amount),
                "可用余额不足", "move-available-to-escrow");
        accountMapper.addAvailable(escrow.getId(), amount);
        record(user.getId(), businessKey + ":source", "ESCROW_PAYMENT",
                amount.negate(), BigDecimal.ZERO, userId);
        record(escrow.getId(), businessKey + ":escrow", "ESCROW_RECEIVE",
                amount, BigDecimal.ZERO, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void transferFrozenToUser(long fromUserId, long toUserId, BigDecimal amount, String businessKey) {
        // 用户间赔付必须按统一账户顺序加锁，避免 A 赔 B 与 B 赔 A 并发时形成死锁。
        if (fromUserId == toUserId) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "资金转出和转入账户不能相同");
        }
        List<WalletAccountDO> locked = lockUserAccounts(fromUserId, toUserId);
        WalletAccountDO from = locked.get(0);
        WalletAccountDO to = locked.get(1);
        if (alreadyRecorded(from.getId(), businessKey + ":source")) {
            return;
        }
        requireAffected(accountMapper.consumeFrozen(from.getId(), amount),
                "冻结余额不足", "transfer-frozen");
        accountMapper.addAvailable(to.getId(), amount);
        record(from.getId(), businessKey + ":source", "COMPENSATION_OUT",
                BigDecimal.ZERO, amount.negate(), toUserId);
        record(to.getId(), businessKey + ":target", "COMPENSATION_IN",
                amount, BigDecimal.ZERO, fromUserId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void transferEscrowToAvailable(long toUserId, BigDecimal amount, String businessKey) {
        // 担保账户和卖家账户同样按 account_code 固定顺序加锁，保证结算与赔付争用同一担保余额时有序。
        WalletAccountDO escrow = accountMapper.selectByCodeForUpdate(ESCROW_ACCOUNT);
        WalletAccountDO user = accountMapper.selectByCodeForUpdate(userAccountCode(toUserId));
        if (alreadyRecorded(escrow.getId(), businessKey + ":source")) {
            return;
        }
        requireAffected(accountMapper.spendAvailable(escrow.getId(), amount),
                "担保账户余额不足", "transfer-escrow");
        accountMapper.addAvailable(user.getId(), amount);
        record(escrow.getId(), businessKey + ":source", "ESCROW_RELEASE",
                amount.negate(), BigDecimal.ZERO, toUserId);
        record(user.getId(), businessKey + ":target", "ESCROW_RECEIVE",
                amount, BigDecimal.ZERO, toUserId);
    }

    private List<WalletAccountDO> lockUserAndEscrow(long userId) {
        String userCode = userAccountCode(userId);
        // 用户账户与担保账户也按 account_code 固定顺序加锁，避免反向资金流形成死锁。
        if (userCode.compareTo(ESCROW_ACCOUNT) < 0) {
            WalletAccountDO user = requireAccountForUpdate(userCode);
            WalletAccountDO escrow = requireAccountForUpdate(ESCROW_ACCOUNT);
            return List.of(user, escrow);
        }
        WalletAccountDO escrow = requireAccountForUpdate(ESCROW_ACCOUNT);
        WalletAccountDO user = requireAccountForUpdate(userCode);
        return List.of(user, escrow);
    }

    /** 用户之间转账始终按 account_code 排序加锁，避免 A->B 与 B->A 并发时互相等待。 */
    private List<WalletAccountDO> lockUserAccounts(long firstUserId, long secondUserId) {
        String firstCode = userAccountCode(firstUserId);
        String secondCode = userAccountCode(secondUserId);
        if (firstCode.compareTo(secondCode) < 0) {
            WalletAccountDO first = requireAccountForUpdate(firstCode);
            WalletAccountDO second = requireAccountForUpdate(secondCode);
            return List.of(first, second);
        }
        WalletAccountDO second = requireAccountForUpdate(secondCode);
        WalletAccountDO first = requireAccountForUpdate(firstCode);
        return List.of(first, second);
    }

    private WalletAccountDO requireAccountForUpdate(String accountCode) {
        WalletAccountDO account = accountMapper.selectByCodeForUpdate(accountCode);
        if (account == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "钱包账户不存在");
        }
        return account;
    }

    private boolean alreadyRecorded(long accountId, String businessKey) {
        return transactionMapper.countBusinessKey(accountId, businessKey) > 0;
    }

    private void requireAffected(int affected, String message, String operation) {
        if (affected == 0) {
            // 资金命令以 SQL 影响行数为唯一成功依据；不能先查余额再假设更新成功。
            metrics.recordWalletFailure(operation);
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, message);
        }
    }

    private void record(long accountId, String businessKey, String type,
                        BigDecimal availableChange, BigDecimal frozenChange, long relatedUserId) {
        // 流水保存本次命令后的可用/冻结余额快照，业务键唯一约束负责重试时避免重复入账。
        WalletAccountDO account = accountMapper.selectByIdForUpdate(accountId);
        WalletTransactionDO transaction = new WalletTransactionDO();
        transaction.setAccountId(accountId);
        transaction.setBusinessKey(businessKey);
        transaction.setTransactionType(type);
        transaction.setAvailableChange(availableChange);
        transaction.setFrozenChange(frozenChange);
        transaction.setAvailableAfter(account.getAvailableBalance());
        transaction.setFrozenAfter(account.getFrozenBalance());
        transaction.setRemark("relatedUserId=" + relatedUserId);
        transactionMapper.insert(transaction);
    }

    public static String userAccountCode(long userId) {
        return WalletService.userAccountCode(userId);
    }
}
