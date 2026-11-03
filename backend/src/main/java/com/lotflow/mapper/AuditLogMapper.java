package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.AuditLogDO;
import org.apache.ibatis.annotations.Mapper;

/** 审核、信用和人工处置审计持久化。 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogDO> {
}
