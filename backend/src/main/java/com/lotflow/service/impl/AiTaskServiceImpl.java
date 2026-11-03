package com.lotflow.service.impl;

import com.lotflow.service.AiTaskService;
import com.lotflow.service.OperationMetricsService;

import com.lotflow.common.BusinessException;
import com.lotflow.common.AfterCommit;
import com.lotflow.common.ResultCode;
import com.lotflow.dto.ListingDraftRequestDTO;
import com.lotflow.dto.SellerAnalysisRequestDTO;
import com.lotflow.entity.AiTaskDO;
import com.lotflow.entity.enums.DomainEnums.AiTaskStatus;
import com.lotflow.mapper.AiTaskMapper;
import com.lotflow.vo.AiTaskVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;

/** AI 任务创建和异步投递。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiTaskServiceImpl implements AiTaskService {

    private final AiTaskMapper taskMapper;
    private final AiWorker worker;
    @Qualifier("aiTaskExecutor")
    private final TaskExecutor taskExecutor;
    private final OperationMetricsService metrics;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public long submitListingDraft(long userId, ListingDraftRequestDTO request) {
        long taskId = createTask(userId, null, null, "LISTING_DRAFT",
                truncate(request.sellerDescription(), 500));
        dispatch(taskId, () -> worker.listingDraft(taskId, request));
        return taskId;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public long createReviewTask(long listingId, long reviewGeneration) {
        // 审核任务由商品事务创建，真正执行通过 Outbox + RabbitMQ 投递，服务重启不会丢失。
        return createTask(null, listingId, reviewGeneration, "LISTING_REVIEW",
                "listingId=" + listingId + ",generation=" + reviewGeneration);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public long createChatRiskTask(long messageId) {
        return createTask(null, null, null, "CHAT_RISK", "messageId=" + messageId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public long submitSellerAnalysis(long userId, SellerAnalysisRequestDTO request) {
        long taskId = createTask(userId, request.listingId(), null, "SELLER_ANALYSIS",
                "listingId=" + request.listingId());
        dispatch(taskId, () -> worker.sellerAnalysis(taskId, request));
        return taskId;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public long submitDisputeSummary(long userId, long orderId) {
        long taskId = createTask(userId, null, null, "DISPUTE_SUMMARY", "orderId=" + orderId);
        dispatch(taskId, () -> worker.disputeSummary(taskId, orderId));
        return taskId;
    }

    @Transactional(readOnly = true)
    @Override
    public AiTaskVO get(long userId, boolean privileged, long taskId) {
        AiTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "AI 任务不存在");
        }
        if (!privileged && (task.getUserId() == null || !task.getUserId().equals(userId))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该 AI 任务");
        }
        return AiTaskVO.from(task);
    }

    private long createTask(Long userId, Long listingId, Long reviewGeneration,
                            String type, String inputSummary) {
        AiTaskDO task = new AiTaskDO();
        task.setUserId(userId);
        task.setListingId(listingId);
        task.setReviewGeneration(reviewGeneration);
        task.setTaskType(type);
        task.setStatus(AiTaskStatus.QUEUED);
        task.setInputSummary(inputSummary);
        taskMapper.insert(task);
        metrics.recordAiTask(type, "queued");
        return task.getId();
    }

    private void dispatch(long taskId, Runnable task) {
        AfterCommit.run(() -> {
            try {
                // 只有任务记录提交后才允许工作线程读取，否则工作线程可能看不到 QUEUED 行。
                taskExecutor.execute(task);
            } catch (TaskRejectedException exception) {
                // 线程池拒绝时保留任务行并标记降级，调用方仍可通过任务接口查看结果。
                log.warn("ai task rejected, taskId={}", taskId);
                AiTaskDO entity = taskMapper.selectById(taskId);
                entity.setStatus(AiTaskStatus.FALLBACK);
                entity.setErrorCode("TASK_REJECTED");
                taskMapper.updateById(entity);
                metrics.recordAiTask(entity.getTaskType(), "rejected");
            }
        });
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
