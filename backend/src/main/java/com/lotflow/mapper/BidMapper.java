package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.BidDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 出价持久化。 */
@Mapper
public interface BidMapper extends BaseMapper<BidDO> {

    /** 出价记录按拍卖 ID 和 ID 游标倒序分页，前端可稳定展示最新出价而不用 offset。 */

    @Select("""
            <script>
            select id, auction_id, bidder_id, amount, deposit_required, idempotency_key,
                   status, gmt_create, gmt_modified, is_deleted, version
            from bid
            where auction_id = #{auctionId}
              <if test="cursor != null">and id &lt; #{cursor}</if>
              and is_deleted = false
            order by id desc
            limit #{limit}
            </script>
            """)
    List<BidDO> selectBidPage(@Param("auctionId") long auctionId,
                              @Param("cursor") Long cursor,
                              @Param("limit") int limit);

    /** 幂等重试需要按首次生成的 bidId 读取原出价，不能重新计算价格或保证金。 */

    @Select("""
            select id, auction_id, bidder_id, amount, deposit_required, idempotency_key,
                   status, gmt_create, gmt_modified, is_deleted, version
            from bid
            where id = #{bidId} and is_deleted = false
            """)
    BidDO selectBid(@Param("bidId") long bidId);

    /** 先按买家取各自最高有效出价，再按金额倒序 offset 1 得到第二高竞买人，保证同一买家多次加价不占多个名次。 */

    @Select("""
            select b.id, b.auction_id, b.bidder_id, b.amount, b.deposit_required,
                   b.idempotency_key, b.status, b.gmt_create, b.gmt_modified,
                   b.is_deleted, b.version
            from bid b
            join (
                select bidder_id, max(amount) as max_amount
                from bid
                where auction_id = #{auctionId}
                  and status = 'VALID'
                  and is_deleted = false
                group by bidder_id
            ) best on best.bidder_id = b.bidder_id and best.max_amount = b.amount
            where b.auction_id = #{auctionId} and b.is_deleted = false
            order by b.amount desc, b.id asc
            limit 1 offset 1
            """)
    BidDO selectSecondHighest(@Param("auctionId") long auctionId);
}
