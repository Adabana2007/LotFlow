package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.NotificationDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 站内通知持久化。 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationDO> {

    /** 业务键由事件和收件人组成，消费者重复投递时由唯一索引直接忽略。 */
    @Insert("""
            insert into notification (user_id, notification_type, title, content,
                                      resource_type, resource_id, biz_key)
            values (#{userId}, #{notificationType}, #{title}, #{content},
                    #{resourceType}, #{resourceId}, #{bizKey})
            on conflict (biz_key) do nothing
            """)
    int insertIfAbsent(NotificationDO notification);

    /** 通知列表按 ID 倒序游标分页，未读优先由客户端过滤，避免排序字段与索引不一致。 */
    @Select("""
            <script>
            select id, user_id, notification_type, title, content, resource_type, resource_id,
                   biz_key, is_read, gmt_create, gmt_modified, is_deleted, version
            from notification
            where user_id = #{userId} and is_deleted = false
              <if test="cursor != null">and id &lt; #{cursor}</if>
            order by id desc
            limit #{limit}
            </script>
            """)
    List<NotificationDO> selectPage(@Param("userId") long userId,
                                    @Param("cursor") Long cursor,
                                    @Param("limit") int limit);

    @Select("""
            select count(1)
            from notification
            where user_id = #{userId} and is_read = false and is_deleted = false
            """)
    long countUnread(@Param("userId") long userId);

    /** 已读是幂等动作：影响行数为零说明已被读或不属于当前用户，均不改动其他记录。 */
    @Update("""
            update notification
            set is_read = true, gmt_modified = current_timestamp
            where id = #{notificationId} and user_id = #{userId}
              and is_read = false and is_deleted = false
            """)
    int markRead(@Param("userId") long userId, @Param("notificationId") long notificationId);
}
