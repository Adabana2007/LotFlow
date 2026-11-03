package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 审核、信用和运营处置共用的审计事实。 */
@Getter
@Setter
@TableName("audit_log")
public class AuditLogDO extends BaseDO {
    private Long actorId;
    private Long subjectUserId;
    private String action;
    private String resourceType;
    private Long resourceId;
    private String beforeState;
    private String afterState;
    private Integer changeValue;
    private String reason;
}
