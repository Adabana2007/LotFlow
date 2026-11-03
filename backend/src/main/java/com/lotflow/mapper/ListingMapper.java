package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.ListingDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 商品、审核快照和向量索引状态持久化。 */
@Mapper
public interface ListingMapper extends BaseMapper<ListingDO> {

    @Select("""
            select id, seller_id, title, description, category_code, condition_code, defects,
                   start_price, duration_hours, scheduled_start, is_auto_downgrade,
                   seckill_price, shop_price, chat_enabled, status, rejection_reason,
                   images_json, review_status, review_generation, risk_level, review_evidence,
                   manual_required, review_decision, review_decision_source, reviewed_by,
                   reviewed_at, vector_status, vector_indexed_at, vector_retry_count,
                   vector_last_error, gmt_create, gmt_modified, is_deleted, version
            from listing
            where id = #{listingId} and is_deleted = false
            for update
            """)
    ListingDO selectByIdForUpdate(@Param("listingId") long listingId);

    /** 公开列表直接换算 ListingVO，必须取齐 VO 依赖的列，缺列会触发自动拆箱空指针。 */
    @Select("""
            <script>
            select id, seller_id, title, description, category_code, condition_code, defects,
                   start_price, duration_hours, scheduled_start, is_auto_downgrade,
                   seckill_price, shop_price, status, rejection_reason,
                   images_json,
                   gmt_create, gmt_modified, version
            from listing
            where is_deleted = false
              and status in ('SCHEDULED', 'AUCTION_LIVE', 'SECKILL', 'SHOP')
              <if test="cursor != null">and id &lt; #{cursor}</if>
            order by id desc
            limit #{limit}
            </script>
            """)
    List<ListingDO> selectVisiblePage(@Param("cursor") Long cursor, @Param("limit") int limit);

    @Select("""
            <script>
            select id, seller_id, title, description, category_code, condition_code, defects,
                   start_price, duration_hours, scheduled_start, is_auto_downgrade,
                   seckill_price, shop_price, status, rejection_reason,
                   images_json,
                   gmt_create, gmt_modified, version
            from listing
            where is_deleted = false
              and status in ('SCHEDULED', 'AUCTION_LIVE', 'SECKILL', 'SHOP')
              and (title ilike concat('%', #{keyword}, '%')
                   or description ilike concat('%', #{keyword}, '%'))
              <if test="cursor != null">and id &lt; #{cursor}</if>
            order by id desc
            limit #{limit}
            </script>
            """)
    List<ListingDO> searchVisiblePage(@Param("keyword") String keyword,
                                      @Param("cursor") Long cursor,
                                      @Param("limit") int limit);

    @Select("""
            <script>
            select id, seller_id, title, description, category_code, condition_code, defects,
                   start_price, duration_hours, scheduled_start, is_auto_downgrade,
                   seckill_price, shop_price, status, rejection_reason,
                   images_json,
                   gmt_create, gmt_modified, version
            from listing
            where is_deleted = false
              and status in ('SCHEDULED', 'AUCTION_LIVE', 'SECKILL', 'SHOP')
              and id in
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<ListingDO> selectVisibleByIds(@Param("ids") List<Long> ids);

    /** 图片是商品 JSON 快照中的值对象，使用独立序列分配稳定图片 ID。 */
    @Select("select nextval('listing_image_id_seq')")
    long nextImageId();

    /** 审核任务 ID 复用商品 ID，后台只读取已经提交且仍待处理的商品审核快照。 */
    @Select("""
            <script>
            select id, seller_id, title, description, category_code, condition_code, defects,
                   start_price, seckill_price, shop_price, status, review_status,
                   review_generation, risk_level, review_evidence, manual_required,
                   gmt_create, gmt_modified
            from listing
            where is_deleted = false and status = 'PENDING_REVIEW'
              <if test="cursor != null">and id &lt; #{cursor}</if>
            order by id desc
            limit #{limit}
            </script>
            """)
    List<ListingDO> selectReviewPage(@Param("cursor") Long cursor, @Param("limit") int limit);

    /** 向量库故障只影响检索，补偿任务按商品行状态重试，不反向阻塞交易。 */
    @Select("""
            select id, seller_id, title, description, category_code, condition_code,
                   status, vector_status, vector_retry_count
            from listing
            where vector_status = 'FAILED'
              and vector_retry_count < 10
              and is_deleted = false
            order by id
            limit 50
            """)
    List<ListingDO> selectRetryableVectors();
}
