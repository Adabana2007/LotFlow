package com.lotflow.service.impl;

import com.lotflow.service.AiResultService;
import com.lotflow.service.OperationMetricsService;
import com.lotflow.service.ReviewService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotflow.ai.chat.ChatRiskAiResult;
import com.lotflow.ai.gateway.AiGateway;
import com.lotflow.entity.AiTaskDO;
import com.lotflow.entity.MessageDO;
import com.lotflow.entity.enums.DomainEnums.AiTaskStatus;
import com.lotflow.mapper.AiTaskMapper;
import com.lotflow.mapper.MessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

/** AI 调用结果持久化。 */
@Service
@RequiredArgsConstructor
public class AiResultServiceImpl implements AiResultService {

    private final AiTaskMapper taskMapper;
    private final MessageMapper messageMapper;
    private final ReviewService reviewService;
    private final ObjectMapper objectMapper;
    private final OperationMetricsService metrics;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void markRunning(long taskId) {
        AiTaskDO task = taskMapper.selectById(taskId);
        task.setStatus(AiTaskStatus.RUNNING);
        taskMapper.updateById(task);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void completeListingDraft(long taskId, Object output, AiGateway.AiCallResult<?> call,
                                     String promptVersion) {
        complete(taskId, output, call, promptVersion);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void completeReview(long taskId, long listingId, long reviewGeneration, Object output,
                               AiGateway.AiCallResult<?> call, String promptVersion,
                               boolean policyAutoApprovalEligible) {
        complete(taskId, output, call, promptVersion);
        reviewService.applyAiRisk(listingId, reviewGeneration,
                ((com.lotflow.ai.review.ReviewRiskAiResult) output).riskLevel(),
                ((com.lotflow.ai.review.ReviewRiskAiResult) output).evidenceSummary(),
                policyAutoApprovalEligible);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void completeChatRisk(long taskId, long messageId, ChatRiskAiResult output,
                                 AiGateway.AiCallResult<?> call, String promptVersion) {
        complete(taskId, output, call, promptVersion);
        MessageDO message = messageMapper.selectById(messageId);
        message.setRiskLevel(output.riskLevel());
        message.setRiskCategories(String.join(",",
                output.categories() == null ? java.util.List.of() : output.categories()));
        message.setRiskEvidence(output.evidenceSummary());
        message.setRequiresHumanReview(output.riskLevel() != com.lotflow.entity.enums.DomainEnums.RiskLevel.LOW);
        messageMapper.updateById(message);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void completeGeneric(long taskId, Object output, AiGateway.AiCallResult<?> call,
                                String promptVersion) {
        complete(taskId, output, call, promptVersion);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void fail(long taskId, String errorCode) {
        AiTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus(AiTaskStatus.FALLBACK);
        task.setErrorCode(errorCode);
        taskMapper.updateById(task);
        metrics.recordAiTask(task.getTaskType(), "fallback");
    }

    private void complete(long taskId, Object output, AiGateway.AiCallResult<?> call,
                          String promptVersion) {
        AiTaskDO task = taskMapper.selectById(taskId);
        task.setStatus(AiTaskStatus.SUCCEEDED);
        // 结果摘要只保存截断后的结构化输出，完整聊天和隐私正文不进入任务表。
        task.setOutputSummary(truncate(toJson(output), 8000));
        task.setModelName(call.model());
        task.setPromptVersion(promptVersion);
        task.setPromptTokens(call.promptTokens());
        task.setCompletionTokens(call.completionTokens());
        task.setLatencyMs(call.latencyMs());
        task.setTraceId(call.traceId());
        taskMapper.updateById(task);
        metrics.recordAiTask(task.getTaskType(), "succeeded");
    }

    private String toJson(Object output) {
        try {
            return objectMapper.writeValueAsString(output);
        } catch (JsonProcessingException exception) {
            return "{\"serializationError\":true}";
        }
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
