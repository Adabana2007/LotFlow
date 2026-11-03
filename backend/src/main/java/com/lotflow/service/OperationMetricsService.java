package com.lotflow.service;

import java.time.Duration;

/** 核心交易和异步链路指标记录。 */
public interface OperationMetricsService {

    void refreshBacklogGauges();

    void recordBidConflict();

    void recordWalletFailure(String operation);

    void recordOutboxPublish(String result);

    void recordAuctionClose(Duration duration, String result);

    void recordAiTask(String type, String result);

    void recordMessageConsumed(String consumer, String result);

    void recordRateLimited(String group);

    void recordRequestRejected(String reason);
}
