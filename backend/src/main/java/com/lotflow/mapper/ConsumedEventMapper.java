package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.ConsumedEventDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 消费者幂等与可恢复处理状态。 */
@Mapper
public interface ConsumedEventMapper extends BaseMapper<ConsumedEventDO> {

    /**
     * 首次处理、失败重试和超时 PROCESSING 记录都可以重新抢占；已成功记录返回 0，消费直接跳过。
     */
    @Select("""
            insert into consumed_event
                (event_id, consumer_name, status, retry_count, started_at)
            values
                (#{eventId}, #{consumerName}, 'PROCESSING', 0, statement_timestamp())
            on conflict (event_id, consumer_name) do update
            set status = 'PROCESSING',
                retry_count = consumed_event.retry_count + 1,
                last_error = null,
                started_at = statement_timestamp(),
                completed_at = null
            where consumed_event.status = 'FAILED'
               or (consumed_event.status = 'PROCESSING'
                   and consumed_event.started_at < statement_timestamp() - interval '5 minutes')
            returning id
            """)
    Long tryStart(@Param("eventId") long eventId,
                  @Param("consumerName") String consumerName);

    @Update("""
            update consumed_event
            set status = 'SUCCEEDED', completed_at = statement_timestamp(), last_error = null
            where event_id = #{eventId} and consumer_name = #{consumerName}
              and status = 'PROCESSING'
            """)
    int markSucceeded(@Param("eventId") long eventId,
                      @Param("consumerName") String consumerName);

    @Update("""
            update consumed_event
            set status = 'FAILED', completed_at = statement_timestamp(), last_error = #{error}
            where event_id = #{eventId} and consumer_name = #{consumerName}
              and status = 'PROCESSING'
            """)
    int markFailed(@Param("eventId") long eventId,
                   @Param("consumerName") String consumerName,
                   @Param("error") String error);
}
