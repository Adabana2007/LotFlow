package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.WalletTransactionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 不可变资金流水持久化。 */
@Mapper
public interface WalletTransactionMapper extends BaseMapper<WalletTransactionDO> {

    /** 资金命令先按账户 + 业务键检查流水，幂等键碰撞时不能再次改变余额。 */
    @Select("""
            select count(1) from wallet_transaction
            where account_id = #{accountId} and business_key = #{businessKey}
            """)
    int countBusinessKey(@Param("accountId") long accountId, @Param("businessKey") String businessKey);

    /** 用户资金流水按 ID 游标倒序分页，业务键和变更后余额保证每笔资金动作可追溯。 */
    @Select("""
            <script>
            select id, account_id, business_key, transaction_type, available_change,
                   frozen_change, available_after, frozen_after, remark, gmt_create
            from wallet_transaction
            where account_id = #{accountId}
              <if test="cursor != null">and id &lt; #{cursor}</if>
            order by id desc
            limit #{limit}
            </script>
            """)
    List<WalletTransactionDO> selectPage(@Param("accountId") long accountId,
                                         @Param("cursor") Long cursor,
                                         @Param("limit") int limit);

    /** 管理员资金审计跨越全部账户，仅做游标只读查询，不提供修改流水的入口。 */
    @Select("""
            <script>
            select id, account_id, business_key, transaction_type, available_change,
                   frozen_change, available_after, frozen_after, remark, gmt_create
            from wallet_transaction
            where 1 = 1
              <if test="cursor != null">and id &lt; #{cursor}</if>
            order by id desc
            limit #{limit}
            </script>
            """)
    List<WalletTransactionDO> selectAdminPage(@Param("cursor") Long cursor,
                                              @Param("limit") int limit);
}
