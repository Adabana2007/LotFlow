package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.SaleChannelDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 销售渠道与流拍决定持久化。 */
@Mapper
public interface SaleChannelMapper extends BaseMapper<SaleChannelDO> {

    @Select("""
            select id, listing_id, channel, status, price, starts_at, ends_at, order_id,
                   decision_status, decision_value, decision_expires_at,
                   gmt_create, gmt_modified, is_deleted, version
            from sale_channel
            where id = #{channelId} and is_deleted = false
            for update
            """)
    SaleChannelDO selectByIdForUpdate(@Param("channelId") long channelId);

    @Select("""
            select id, listing_id, channel, status, price, starts_at, ends_at, order_id,
                   decision_status, decision_value, decision_expires_at,
                   gmt_create, gmt_modified, is_deleted, version
            from sale_channel
            where listing_id = #{listingId}
              and status in ('SCHEDULED', 'ACTIVE', 'SOLD')
              and is_deleted = false
            order by id desc
            limit 1
            for update
            """)
    SaleChannelDO selectActiveForUpdate(@Param("listingId") long listingId);

    @Select("""
            select id, listing_id, channel, status, price, starts_at, ends_at, order_id,
                   decision_status, decision_value, decision_expires_at,
                   gmt_create, gmt_modified, is_deleted, version
            from sale_channel
            where listing_id = #{listingId} and is_deleted = false
            order by id desc
            limit 1
            for update
            """)
    SaleChannelDO selectLatestForUpdate(@Param("listingId") long listingId);

    @Select("""
            select id, listing_id, channel, status, price, starts_at, ends_at, order_id,
                   decision_status, decision_value, decision_expires_at,
                   gmt_create, gmt_modified, is_deleted, version
            from sale_channel
            where status = 'ACTIVE' and ends_at <= statement_timestamp() and is_deleted = false
            order by id
            limit 100
            """)
    List<SaleChannelDO> selectDue();

    /** 卖家决定属于已结束渠道行的一部分，锁该行可阻止迟到请求并发启动两个新渠道。 */
    @Select("""
            select id, listing_id, channel, status, price, starts_at, ends_at, order_id,
                   decision_status, decision_value, decision_expires_at,
                   gmt_create, gmt_modified, is_deleted, version
            from sale_channel
            where listing_id = #{listingId}
              and decision_status = 'WAITING'
              and is_deleted = false
            order by id desc
            limit 1
            for update
            """)
    SaleChannelDO selectWaitingDecisionForUpdate(@Param("listingId") long listingId);

    @Select("""
            select id, listing_id, channel, status, price, starts_at, ends_at, order_id,
                   decision_status, decision_value, decision_expires_at,
                   gmt_create, gmt_modified, is_deleted, version
            from sale_channel
            where decision_status = 'WAITING'
              and decision_expires_at <= statement_timestamp()
              and is_deleted = false
            order by id
            limit 100
            """)
    List<SaleChannelDO> selectExpiredDecisions();
}
