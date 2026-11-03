package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 用户账号和信用事实持久化。 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {

    /** 同一入口同时接受用户名和手机号，数据库仍分别建立唯一索引。 */
    @Select("""
            select id, username, phone, password_hash, display_name, role_code, status,
                   chat_enabled, completed_review_count, credit_score,
                   avatar_object_key, notify_auction_ending, notify_order_payment,
                   notify_messages,
                   gmt_create, gmt_modified, is_deleted
            from app_user
            where (username = #{account} or phone = #{account}) and is_deleted = false
            """)
    UserDO selectByAccount(@Param("account") String account);

    @Select("""
            select id, username, phone, password_hash, display_name, role_code, status,
                   chat_enabled, completed_review_count, credit_score,
                   avatar_object_key, notify_auction_ending, notify_order_payment,
                   notify_messages,
                   gmt_create, gmt_modified, is_deleted
            from app_user
            where phone = #{phone} and is_deleted = false
            """)
    UserDO selectByPhone(@Param("phone") String phone);

    @Select("""
            select id, username, phone, password_hash, display_name, role_code, status,
                   chat_enabled, completed_review_count, credit_score,
                   avatar_object_key, notify_auction_ending, notify_order_payment,
                   notify_messages,
                   gmt_create, gmt_modified, is_deleted
            from app_user
            where username = #{username} and is_deleted = false
            """)
    UserDO selectByUsername(@Param("username") String username);

    @Select("""
            <script>
            select id, username, phone, password_hash, display_name, role_code, status,
                   chat_enabled, completed_review_count, credit_score,
                   avatar_object_key, notify_auction_ending, notify_order_payment,
                   notify_messages,
                   gmt_create, gmt_modified, is_deleted
            from app_user
            where is_deleted = false
              <if test="cursor != null">and id &lt; #{cursor}</if>
            order by id desc
            limit #{limit}
            </script>
            """)
    List<UserDO> selectPage(@Param("cursor") Long cursor, @Param("limit") int limit);

    /** 运营处置先锁用户行，保证状态和版本检查基于同一份账号事实。 */
    @Select("""
            select id, username, phone, password_hash, display_name, role_code, status,
                   chat_enabled, completed_review_count, credit_score,
                   avatar_object_key, notify_auction_ending, notify_order_payment,
                   notify_messages,
                   gmt_create, gmt_modified, is_deleted
            from app_user
            where id = #{userId} and is_deleted = false
            for update
            """)
    UserDO selectByIdForUpdate(@Param("userId") long userId);

    /** 同一卖家可能并发通过多个商品，计数必须由数据库原子递增而不是先查后写。 */
    @Update("""
            update app_user
            set completed_review_count = completed_review_count + 1,
                gmt_modified = current_timestamp
            where id = #{userId} and is_deleted = false
            """)
    int incrementCompletedReviewCount(@Param("userId") long userId);

    /** 信用分只允许服务端边界收敛，每次变化由同一事务写审计日志。 */
    @Update("""
            update app_user
            set credit_score = greatest(0, least(120, credit_score + #{changeValue})),
                gmt_modified = current_timestamp
            where id = #{userId} and is_deleted = false
            """)
    int adjustCredit(@Param("userId") long userId, @Param("changeValue") int changeValue);
}
