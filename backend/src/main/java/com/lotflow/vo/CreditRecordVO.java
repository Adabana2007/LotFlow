package com.lotflow.vo;

import com.lotflow.entity.AuditLogDO;

import java.time.OffsetDateTime;

/** 信用变化记录。 */
public record CreditRecordVO(String id, int changeValue, String reason,
                             String relatedType, String relatedId, OffsetDateTime occurredAt) {
    public static CreditRecordVO from(AuditLogDO record) {
        return new CreditRecordVO(record.getId().toString(), record.getChangeValue(),
                record.getReason(), record.getResourceType(),
                record.getResourceId() == null ? null : record.getResourceId().toString(),
                record.getGmtCreate());
    }
}
