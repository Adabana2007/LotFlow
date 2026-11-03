package com.lotflow.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** WebSocket 等副作用必须等待数据库事务提交。 */
public final class AfterCommit {

    private static final Logger LOG = LoggerFactory.getLogger(AfterCommit.class);

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSafely(action);
                }
            });
            return;
        }
        runSafely(action);
    }

    private static void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            // 数据库已经提交，推送或异步投递失败不能反向改变交易结果。
            LOG.warn("after-commit action failed, reason={}",
                    exception.getClass().getSimpleName());
        }
    }
}
