package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotflow.entity.enums.DomainEnums.RoleCode;
import com.lotflow.entity.enums.DomainEnums.UserStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 平台用户及其唯一业务角色。 */
@Getter
@Setter
@TableName("app_user")
public class UserDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String phone;
    private String passwordHash;
    private String displayName;
    private RoleCode roleCode;
    private UserStatus status;
    private Boolean chatEnabled;
    private Integer completedReviewCount;
    private Integer creditScore;
    private String avatarObjectKey;
    private Boolean notifyAuctionEnding;
    private Boolean notifyOrderPayment;
    private Boolean notifyMessages;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime gmtCreate;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime gmtModified;
    @TableLogic
    private Boolean isDeleted;
}
