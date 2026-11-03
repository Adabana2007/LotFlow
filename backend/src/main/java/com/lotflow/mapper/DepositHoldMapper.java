package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.DepositHoldDO;
import com.lotflow.entity.enums.DomainEnums.DepositType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 保证金冻结记录持久化。 */
@Mapper
public interface DepositHoldMapper extends BaseMapper<DepositHoldDO> {

    /** 同一拍卖、用户和保证金类型只取一条并加锁，防止重复冻结或重复赔付同一竞买人。 */

    @Select("""
            select id, auction_id, user_id, deposit_type, amount, status, business_key,
                   gmt_create, gmt_modified, is_deleted, version
            from deposit_hold
            where auction_id = #{auctionId}
              and user_id = #{userId}
              and deposit_type = #{depositType}
              and is_deleted = false
            for update
            """)
    DepositHoldDO selectForUpdate(@Param("auctionId") long auctionId,
                                 @Param("userId") long userId,
                                 @Param("depositType") DepositType depositType);

    /** 关单或取消时只读取仍处于 HELD 的保证金，已释放或已赔付记录不能再次产生资金动作。 */

    @Select("""
            select id, auction_id, user_id, deposit_type, amount, status, business_key,
                   gmt_create, gmt_modified, is_deleted, version
            from deposit_hold
            where auction_id = #{auctionId} and status = 'HELD' and is_deleted = false
            order by id
            """)
    List<DepositHoldDO> selectHeldByAuction(@Param("auctionId") long auctionId);
}
