package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotflow.common.JsonbStringTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 与本地业务事务一起提交的 Outbox 事件。 */
@Getter
@Setter
@TableName(value = "outbox_event", autoResultMap = true)
public class OutboxEventDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventType;
    private String aggregateType;
    private Long aggregateId;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String payload;
    private String status;
    private OffsetDateTime availableAt;
    private OffsetDateTime publishedAt;
    private Integer retryCount;
    private String lastError;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime gmtCreate;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime gmtModified;
}
