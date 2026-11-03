package com.lotflow.service.impl;

import com.lotflow.service.OperationMetricsService;

import com.lotflow.mapper.OutboxEventMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** 核心交易和异步链路指标记录。 */
@Service
@RequiredArgsConstructor
public class OperationMetricsServiceImpl implements OperationMetricsService {

    private final MeterRegistry meterRegistry;
    private final OutboxEventMapper outboxEventMapper;
    @Qualifier("aiTaskExecutor")
    private final ThreadPoolTaskExecutor aiTaskExecutor;
    @Qualifier("messageTaskExecutor")
    private final ThreadPoolTaskExecutor messageTaskExecutor;
    @Qualifier("websocketTaskExecutor")
    private final ThreadPoolTaskExecutor websocketTaskExecutor;
    private final AtomicLong pendingOutboxEvents = new AtomicLong();

    @PostConstruct
    void registerGauges() {
        Gauge.builder("lotflow.outbox.pending", pendingOutboxEvents, AtomicLong::get)
                .description("Pending or failed outbox events")
                .register(meterRegistry);
        queueGauge("lotflow.ai.queue.depth", "AI executor queue depth", aiTaskExecutor);
        queueGauge("lotflow.message.queue.depth", "message executor queue depth",
                messageTaskExecutor);
        queueGauge("lotflow.websocket.queue.depth", "WebSocket executor queue depth",
                websocketTaskExecutor);
    }

    @Scheduled(fixedDelayString = "${lotflow.metrics.refresh-delay:15000}")
    @Override
    public void refreshBacklogGauges() {
        // 积压指标同时覆盖 PENDING 和 FAILED 事件，运维可发现消息未发出或持续重试。
        pendingOutboxEvents.set(outboxEventMapper.countPending());
    }

    @Override
    public void recordBidConflict() {
        // 出价冲突率反映同一拍卖热点和锁竞争，记录后仍由 409 返回最新价格，不自动替用户加价。
        counter("lotflow.auction.bid.conflicts", "auction bid conflicts").increment();
    }

    @Override
    public void recordWalletFailure(String operation) {
        // 资金失败按 operation 标签区分冻结、解冻、担保转账等命令，便于定位具体资金链路。
        counter("lotflow.wallet.failures", "wallet command failures", "operation", operation)
                .increment();
    }

    @Override
    public void recordOutboxPublish(String result) {
        // Outbox 发布成功和失败必须分别计数，broker confirm 失败不能被当作业务事件已可靠投递。
        counter("lotflow.outbox.publishes", "outbox publish attempts", "result", result)
                .increment();
    }

    @Override
    public void recordAuctionClose(Duration duration, String result) {
        // 关单耗时和结果帮助发现拍卖行锁或订单创建导致的尾延迟和失败。
        Timer.builder("lotflow.auction.close")
                .description("Auction close transaction duration")
                .tag("result", result)
                .register(meterRegistry)
                .record(duration);
    }

    @Override
    public void recordAiTask(String type, String result) {
        // AI 任务指标按任务类型和结果标签记录，核心交易不会等待这些统计写入。
        counter("lotflow.ai.tasks", "AI task lifecycle events",
                "type", type, "result", result).increment();
    }

    @Override
    public void recordMessageConsumed(String consumer, String result) {
        // 消息消费结果按消费者分组，重复投递和失败重试应能从该指标中看出来。
        counter("lotflow.message.consumed", "message consumer outcomes",
                "consumer", consumer, "result", result).increment();
    }

    @Override
    public void recordRateLimited(String group) {
        // 限流拒绝按认证、写请求和普通查询分组，便于区分正常业务峰值和接口攻击。
        counter("lotflow.http.rate.limited", "requests rejected by rate limiting", "group", group)
                .increment();
    }

    @Override
    public void recordRequestRejected(String reason) {
        // 请求大小等前置拒绝在进入 Controller 前记录，避免把过滤器层失败误判为业务错误。
        counter("lotflow.http.request.rejected", "requests rejected before controller",
                "reason", reason).increment();
    }

    private Counter counter(String name, String description, String... tags) {
        return Counter.builder(name).description(description).tags(tags).register(meterRegistry);
    }

    private void queueGauge(String name, String description, ThreadPoolTaskExecutor executor) {
        // 读取 executor 队列深度即可发现 AI、消息和推送链路的积压趋势。
        Gauge.builder(name, executor,
                        value -> value.getThreadPoolExecutor().getQueue().size())
                .description(description)
                .register(meterRegistry);
    }
}
