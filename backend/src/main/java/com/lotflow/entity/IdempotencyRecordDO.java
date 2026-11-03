package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 命令幂等状态和生成的资源 ID。 */
@Getter
@Setter
@TableName("idempotency_record")
public class IdempotencyRecordDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String commandScope;
    private String idempotencyKey;
    private String requestHash;
    private Long resourceId;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime gmtCreate;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime gmtModified;
}
