package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.IdempotencyRecordDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 命令幂等记录。 */
@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecordDO> {

    /** 支付、出价等命令先抢占 PROCESSING 记录；唯一键冲突返回 null，由 Service 判断重放还是参数冲突。 */
    @Select("""
            insert into idempotency_record
                (command_scope, idempotency_key, request_hash, status)
            values
                (#{scope}, #{key}, #{requestHash}, 'PROCESSING')
            on conflict (command_scope, idempotency_key) do nothing
            returning id
            """)
    Long insertIfAbsent(@Param("scope") String scope,
                        @Param("key") String key,
                        @Param("requestHash") String requestHash);

    /** 已存在的幂等记录保存请求哈希和首次资源 ID，用于拒绝不同请求体并返回原业务结果。 */
    @Select("""
            select id, command_scope, idempotency_key, request_hash, resource_id, status,
                   gmt_create, gmt_modified
            from idempotency_record
            where command_scope = #{scope} and idempotency_key = #{key}
            """)
    IdempotencyRecordDO selectByScopeAndKey(@Param("scope") String scope, @Param("key") String key);
}
