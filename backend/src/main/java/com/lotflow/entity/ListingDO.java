package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotflow.common.JsonbStringTypeHandler;
import com.lotflow.entity.enums.DomainEnums.ListingStatus;
import com.lotflow.entity.enums.DomainEnums.ReviewDecisionSource;
import com.lotflow.entity.enums.DomainEnums.ReviewDecisionValue;
import com.lotflow.entity.enums.DomainEnums.ReviewStatus;
import com.lotflow.entity.enums.DomainEnums.RiskLevel;
import com.lotflow.entity.enums.DomainEnums.VectorIndexStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 商品、审核快照、图片元数据和销售规则。 */
@Getter
@Setter
@TableName(value = "listing", autoResultMap = true)
public class ListingDO extends BaseDO {
    private Long sellerId;
    private String title;
    private String description;
    private String categoryCode;
    private String conditionCode;
    private String defects;
    private BigDecimal startPrice;
    private Integer durationHours;
    private OffsetDateTime scheduledStart;
    private Boolean isAutoDowngrade;
    private BigDecimal seckillPrice;
    private BigDecimal shopPrice;
    private Boolean chatEnabled;
    private ListingStatus status;
    private String rejectionReason;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String imagesJson;
    private ReviewStatus reviewStatus;
    private Long reviewGeneration;
    private RiskLevel riskLevel;
    private String reviewEvidence;
    private Boolean manualRequired;
    private ReviewDecisionValue reviewDecision;
    private ReviewDecisionSource reviewDecisionSource;
    private Long reviewedBy;
    private OffsetDateTime reviewedAt;
    private VectorIndexStatus vectorStatus;
    private OffsetDateTime vectorIndexedAt;
    private Integer vectorRetryCount;
    private String vectorLastError;
}
