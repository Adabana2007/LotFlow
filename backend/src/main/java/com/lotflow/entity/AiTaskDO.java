package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lotflow.entity.enums.DomainEnums.AiTaskStatus;
import lombok.Getter;
import lombok.Setter;

/** AI 任务审计记录。 */
@Getter
@Setter
@TableName("ai_task")
public class AiTaskDO extends BaseDO {
    private Long userId;
    private Long listingId;
    private Long reviewGeneration;
    private String taskType;
    private AiTaskStatus status;
    private String inputSummary;
    private String outputSummary;
    private String modelName;
    private String promptVersion;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long latencyMs;
    private String traceId;
    private String errorCode;
}
