package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.OrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 订单、支付、履约、争议和秒杀预占统一持久化。 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderDO> {

    @Select("""
            select id, order_no, listing_id, auction_id, seller_id, buyer_id, source_type,
                   amount, deposit_applied, status, payment_deadline, ship_deadline,
                   shipped_at, receipt_deadline, received_at, dispute_deadline, dispute_opened_at,
                   escrow_amount, offered_second_chance, payment_key, paid_at, carrier,
                   tracking_no, dispute_status, dispute_reason, dispute_evidence,
                   dispute_resolution, dispute_resolved_by, dispute_resolved_at,
                   reservation_status, reservation_expires_at, reservation_key,
                   gmt_create, gmt_modified, is_deleted, version
            from order_record
            where id = #{orderId} and is_deleted = false
            for update
            """)
    OrderDO selectByIdForUpdate(@Param("orderId") long orderId);

    @Select("""
            select id, order_no, listing_id, auction_id, seller_id, buyer_id, source_type,
                   amount, deposit_applied, status, payment_deadline, ship_deadline,
                   shipped_at, receipt_deadline, received_at, dispute_deadline, dispute_opened_at,
                   escrow_amount, offered_second_chance, payment_key, paid_at, carrier,
                   tracking_no, dispute_status, dispute_reason, dispute_evidence,
                   dispute_resolution, dispute_resolved_by, dispute_resolved_at,
                   reservation_status, reservation_expires_at, reservation_key,
                   gmt_create, gmt_modified, is_deleted, version
            from order_record
            where status in ('PENDING_PAYMENT', 'PAID', 'SHIPPED', 'RECEIVED', 'SECOND_CHANCE')
              and (
                status = 'PENDING_PAYMENT' and payment_deadline <= statement_timestamp()
                or status = 'SECOND_CHANCE' and payment_deadline <= statement_timestamp()
                or status = 'PAID' and ship_deadline <= statement_timestamp()
                or status = 'SHIPPED' and receipt_deadline <= statement_timestamp()
                or status = 'RECEIVED' and dispute_deadline <= statement_timestamp()
              )
              and is_deleted = false
            order by id
            limit 100
            """)
    List<OrderDO> selectDue();

    @Select("""
            <script>
            select id, order_no, listing_id, auction_id, seller_id, buyer_id, source_type,
                   amount, deposit_applied, status, payment_deadline, ship_deadline,
                   shipped_at, receipt_deadline, received_at, dispute_deadline, dispute_opened_at,
                   escrow_amount, offered_second_chance, payment_key, paid_at, carrier,
                   tracking_no, dispute_status, dispute_reason, dispute_evidence,
                   dispute_resolution, dispute_resolved_by, dispute_resolved_at,
                   reservation_status, reservation_expires_at, reservation_key,
                   gmt_create, gmt_modified, is_deleted, version
            from order_record
            where is_deleted = false
              <if test="cursor != null">and id &lt; #{cursor}</if>
            order by id desc
            limit #{limit}
            </script>
            """)
    List<OrderDO> selectAdminPage(@Param("cursor") Long cursor, @Param("limit") int limit);
}
