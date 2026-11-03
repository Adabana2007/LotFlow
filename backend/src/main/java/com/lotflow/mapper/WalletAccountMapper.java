package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.WalletAccountDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/** 钱包账户原子更新。 */
@Mapper
public interface WalletAccountMapper extends BaseMapper<WalletAccountDO> {

    /** 注册和充值入口可能并发创建同一用户账户，交由数据库唯一约束收敛。 */
    @Insert("""
            insert into wallet_account
                (account_code, owner_type, owner_id, available_balance, frozen_balance)
            values
                (#{accountCode}, 'USER', #{userId}, 0, 0)
            on conflict (account_code) do nothing
            """)
    int insertUserAccountIfAbsent(@Param("accountCode") String accountCode,
                                  @Param("userId") long userId);

    /** 读取账户用于余额展示；不申请行锁，避免普通查询阻塞资金命令。 */

    @Select("""
            select id, account_code, owner_type, owner_id, available_balance, frozen_balance,
                   gmt_create, gmt_modified, is_deleted, version
            from wallet_account
            where account_code = #{accountCode} and is_deleted = false
            """)
    WalletAccountDO selectByCode(@Param("accountCode") String accountCode);

    /** 资金命令先锁定账户行，保证后续余额判断、流水写入和原子更新基于同一账户版本。 */

    @Select("""
            select id, account_code, owner_type, owner_id, available_balance, frozen_balance,
                   gmt_create, gmt_modified, is_deleted, version
            from wallet_account
            where account_code = #{accountCode} and is_deleted = false
            for update
            """)
    WalletAccountDO selectByCodeForUpdate(@Param("accountCode") String accountCode);

    /** 流水写入需要读取更新后的余额快照，因此按账户 ID 加锁后再记录本次变更结果。 */

    @Select("""
            select id, account_code, owner_type, owner_id, available_balance, frozen_balance,
                   gmt_create, gmt_modified, is_deleted, version
            from wallet_account
            where id = #{accountId} and is_deleted = false
            for update
            """)
    WalletAccountDO selectByIdForUpdate(@Param("accountId") long accountId);

    /** 冻结必须在 SQL 内同时检查可用余额；影响行数为 0 代表余额不足，Service 不应继续写流水。 */

    @Update("""
            update wallet_account
            set available_balance = available_balance - #{amount},
                frozen_balance = frozen_balance + #{amount},
                version = version + 1,
                gmt_modified = current_timestamp
            where id = #{accountId}
              and available_balance >= #{amount}
              and is_deleted = false
            """)
    int freeze(@Param("accountId") long accountId, @Param("amount") BigDecimal amount);

    /** 解冻同时增加可用余额、减少冻结余额；条件保证不会释放超过实际冻结额。 */

    @Update("""
            update wallet_account
            set available_balance = available_balance + #{amount},
                frozen_balance = frozen_balance - #{amount},
                version = version + 1,
                gmt_modified = current_timestamp
            where id = #{accountId}
              and frozen_balance >= #{amount}
              and is_deleted = false
            """)
    int releaseFrozen(@Param("accountId") long accountId, @Param("amount") BigDecimal amount);

    /** 可用余额消费采用条件更新，资金不足时返回 0，避免先查后扣产生超扣。 */

    @Update("""
            update wallet_account
            set available_balance = available_balance - #{amount},
                version = version + 1,
                gmt_modified = current_timestamp
            where id = #{accountId}
              and available_balance >= #{amount}
              and is_deleted = false
            """)
    int spendAvailable(@Param("accountId") long accountId, @Param("amount") BigDecimal amount);

    /** 入账只增加可用余额，调用方必须先完成幂等键和业务状态校验。 */

    @Update("""
            update wallet_account
            set available_balance = available_balance + #{amount},
                version = version + 1,
                gmt_modified = current_timestamp
            where id = #{accountId} and is_deleted = false
            """)
    int addAvailable(@Param("accountId") long accountId, @Param("amount") BigDecimal amount);

    /** 已冻结资金直接转出时只减少冻结额；担保入账由另一账户的 addAvailable 在同一事务完成。 */

    @Update("""
            update wallet_account
            set frozen_balance = frozen_balance - #{amount},
                version = version + 1,
                gmt_modified = current_timestamp
            where id = #{accountId}
              and frozen_balance >= #{amount}
              and is_deleted = false
            """)
    int consumeFrozen(@Param("accountId") long accountId, @Param("amount") BigDecimal amount);
}
