package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 业务表公共字段。 */
@Getter
@Setter
public abstract class BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime gmtCreate;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime gmtModified;

    @TableLogic
    private Boolean isDeleted;

    @Version
    private Long version;
}
