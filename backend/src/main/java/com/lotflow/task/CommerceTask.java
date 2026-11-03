package com.lotflow.task;

import com.lotflow.mapper.AuctionMapper;
import com.lotflow.mapper.OrderMapper;
import com.lotflow.mapper.SaleChannelMapper;
import com.lotflow.service.AuctionService;
import com.lotflow.service.ChannelService;
import com.lotflow.service.OrderService;
import com.lotflow.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 交易超时状态的数据库补偿任务。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommerceTask {

    private final AuctionMapper auctionMapper;
    private final SaleChannelMapper channelMapper;
    private final OrderMapper orderMapper;
    private final AuctionService auctionService;
    private final ChannelService channelService;
    private final OrderService orderService;
    private final SeckillService seckillService;

    @Scheduled(fixedDelayString = "${lotflow.tasks.commerce-delay:3000}")
    public void runCommerceCompensation() {
        // 各业务域独立失败，单条超时链路异常不能阻止其他库存和资金事实收敛。
        scan("auction", () -> auctionMapper.selectDue().forEach(row ->
                compensate("auction", row.getId(), () -> auctionService.processAuction(row.getId()))));
        scan("channel", () -> channelMapper.selectDue().forEach(row ->
                compensate("channel", row.getId(), () -> channelService.processDueChannel(row.getId()))));
        scan("decision", () -> channelMapper.selectExpiredDecisions().forEach(row ->
                compensate("decision", row.getId(), () -> channelService.expireDecision(row.getId()))));
        scan("order", () -> orderMapper.selectDue().forEach(row ->
                compensate("order", row.getId(), () -> processOrder(row))));
    }

    private void processOrder(com.lotflow.entity.OrderDO row) {
        switch (row.getStatus()) {
            // 秒杀订单必须先走预占释放：processPendingPayment 只恢复数据库库存，
            // Redis 令牌不删会让商品在令牌 TTL 内无法再次下单。
            case PENDING_PAYMENT, SECOND_CHANCE -> {
                if ("SECKILL".equals(row.getSourceType())) {
                    seckillService.expireReservation(row.getId());
                } else {
                    orderService.processPendingPayment(row.getId());
                }
            }
            case PAID -> orderService.processSellerShipTimeout(row.getId());
            case SHIPPED -> orderService.autoConfirmReceipt(row.getId());
            case RECEIVED -> orderService.completeAfterDisputeWindow(row.getId());
            default -> {
                // 其他状态没有到期待办；查询条件已排除它们，这里保持显式收敛。
            }
        }
    }

    private void scan(String task, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            // 查询本身失败只影响本轮扫描，下一轮继续补偿，不能中断调度线程。
            log.warn("compensation scan failed, task={}", task, exception);
        }
    }

    private void compensate(String task, long id, Runnable action) {
        // 到期记录按 ID 升序重扫，单条异常必须就地收敛，否则同一脏数据会永久堵住后续记录。
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.warn("compensation failed, task={}, id={}", task, id, exception);
        }
    }
}
