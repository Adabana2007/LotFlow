package com.lotflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;

/** 所有交易超时使用数据库时间，避免多实例应用时钟漂移。 */
@Mapper
public interface DatabaseTimeMapper {

    @Select("select statement_timestamp()")
    OffsetDateTime now();
}
