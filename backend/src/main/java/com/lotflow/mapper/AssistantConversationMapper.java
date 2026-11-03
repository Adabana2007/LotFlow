package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.AssistantConversationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/** 助手会话元数据持久化。 */
@Mapper
public interface AssistantConversationMapper extends BaseMapper<AssistantConversationDO> {

    /** 按最近活跃倒序分页；游标使用 (last_message_at, id) 复合条件，避免翻页时漏项。 */
    @Select("""
            <script>
            select id, user_id, title, last_message_at, gmt_create, gmt_modified, is_deleted, version
            from assistant_conversation
            where user_id = #{userId} and is_deleted = false
              <if test="cursorTime != null">
              and (last_message_at, id) &lt; (#{cursorTime}, #{cursorId})
              </if>
            order by last_message_at desc, id desc
            limit #{limit}
            </script>
            """)
    List<AssistantConversationDO> selectPage(@Param("userId") long userId,
                                             @Param("cursorTime") OffsetDateTime cursorTime,
                                             @Param("cursorId") Long cursorId,
                                             @Param("limit") int limit);

    @Select("""
            select id, user_id, title, last_message_at, gmt_create, gmt_modified, is_deleted, version
            from assistant_conversation
            where id = #{conversationId} and is_deleted = false
            """)
    AssistantConversationDO selectActive(@Param("conversationId") long conversationId);

    @Update("""
            update assistant_conversation
            set last_message_at = current_timestamp, gmt_modified = current_timestamp
            where id = #{conversationId} and is_deleted = false
            """)
    int touch(@Param("conversationId") long conversationId);

    @Update("""
            update assistant_conversation
            set title = #{title}, gmt_modified = current_timestamp
            where id = #{conversationId} and is_deleted = false
            """)
    int updateTitle(@Param("conversationId") long conversationId, @Param("title") String title);
}
