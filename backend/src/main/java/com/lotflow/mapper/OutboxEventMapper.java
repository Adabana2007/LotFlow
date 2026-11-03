package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.OutboxEventDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** Outbox 事件持久化。 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEventDO> {

    /** PENDING 和 FAILED 都应计入积压指标，运维需要同时看到未发送和等待重试的事件。 */
    @Select("""
            select count(1)
            from outbox_event
            where status in ('PENDING', 'FAILED')
            """)
    long countPending();

    /** 多实例中继用 FOR UPDATE SKIP LOCKED 抢占可发送事件，避免同一事件被多个实例并发发布。 */
    @Select("""
            select id, event_type, aggregate_type, aggregate_id, payload, status,
                   available_at, published_at, retry_count, last_error,
                   gmt_create, gmt_modified
            from outbox_event
            where status in ('PENDING', 'FAILED')
              and available_at <= statement_timestamp()
            order by id
            limit #{limit}
            for update skip locked
            """)
    List<OutboxEventDO> selectReady(@Param("limit") int limit);

    /** 领取后推迟可发送时间形成租约；短事务提交后其他实例不会再领到同一批事件。 */
    @Update("""
            <script>
            update outbox_event
            set available_at = statement_timestamp() + interval '2 minutes'
            where id in
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int lease(@Param("ids") List<Long> ids);

    @Update("""
            update outbox_event
            set status = 'PUBLISHED',
                published_at = statement_timestamp(),
                last_error = null,
                gmt_modified = statement_timestamp()
            where id = #{eventId}
            """)
    int markPublished(@Param("eventId") long eventId);

    /** 失败事件保留在表中并推迟十秒再试，不能让 broker 未确认的消息被当作已发布。 */
    @Update("""
            update outbox_event
            set status = 'FAILED',
                retry_count = retry_count + 1,
                available_at = statement_timestamp() + interval '10 seconds',
                last_error = #{lastError},
                gmt_modified = statement_timestamp()
            where id = #{eventId}
            """)
    int markFailed(@Param("eventId") long eventId, @Param("lastError") String lastError);
}
