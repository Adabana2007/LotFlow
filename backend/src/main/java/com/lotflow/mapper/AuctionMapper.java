package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.AuctionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

/** 拍卖持久化及行锁查询。 */
@Mapper
public interface AuctionMapper extends BaseMapper<AuctionDO> {

    /** 出价和关单必须锁拍卖行，防止并发请求基于同一个旧最高价和结束时间做决策。 */

    @Select("""
            select id, listing_id, seller_id, attempt_no, start_price, current_price, status,
                   start_time, end_time, extension_seconds, highest_bidder_id, highest_bid_id,
                   gmt_create, gmt_modified, is_deleted, version
            from auction
            where id = #{auctionId} and is_deleted = false
            for update
            """)
    AuctionDO selectByIdForUpdate(@Param("auctionId") long auctionId);

    /** 补偿任务只扫 SCHEDULED/LIVE 且已到数据库时间的场次，每次最多 100 条避免长时间占用连接。 */

    @Select("""
            select id, listing_id, seller_id, attempt_no, start_price, current_price, status,
                   start_time, end_time, extension_seconds, highest_bidder_id, highest_bid_id,
                   gmt_create, gmt_modified, is_deleted, version
            from auction
            where status in ('SCHEDULED', 'LIVE')
              and (status = 'LIVE' and end_time <= statement_timestamp()
                   or status = 'SCHEDULED' and start_time <= statement_timestamp())
              and is_deleted = false
            order by id
            limit 100
            """)
    List<AuctionDO> selectDue();

    /** 使用 PostgreSQL 时间判断开拍、出价和顺延，避免多实例 JVM 时钟漂移。 */

    @Select("select statement_timestamp()")
    OffsetDateTime databaseTime();

    /** 拍卖列表用 ID 游标倒序分页，动态 cursor 条件避免 PostgreSQL 对 null 参数做类型推断。 */

    @Select("""
            <script>
            select id, listing_id, seller_id, attempt_no, start_price, current_price, status,
                   start_time, end_time, extension_seconds, highest_bidder_id, highest_bid_id,
                   gmt_create, gmt_modified, is_deleted, version
            from auction
            where is_deleted = false
              <if test="cursor != null">and id &lt; #{cursor}</if>
              <if test="activeOnly">and status in ('SCHEDULED', 'LIVE')</if>
            order by id desc
            limit #{limit}
            </script>
            """)
    List<AuctionDO> selectPage(@Param("cursor") Long cursor, @Param("limit") int limit,
                               @Param("activeOnly") boolean activeOnly);

    /** 流拍降级需要找到最近一场拍卖，按 listing_id + 倒序 ID 只取一条历史场次。 */

    @Select("""
            select id from auction
            where listing_id = #{listingId} and is_deleted = false
            order by id desc
            limit 1
            """)
    Long selectLatestIdByListing(@Param("listingId") long listingId);
}
