package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.MessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 消息及聊天风险建议持久化。 */
@Mapper
public interface MessageMapper extends BaseMapper<MessageDO> {

    @Select("""
            <script>
            select id, conversation_id, sender_id, sequence_no, content, risk_level,
                   risk_categories, risk_evidence, requires_human_review,
                   gmt_create, gmt_modified, is_deleted, version
            from message
            where conversation_id = #{conversationId}
              <if test="afterSequence != null">and sequence_no &gt; #{afterSequence}</if>
              and is_deleted = false
            order by sequence_no
            limit #{limit}
            </script>
            """)
    List<MessageDO> selectAfter(@Param("conversationId") long conversationId,
                                @Param("afterSequence") Long afterSequence,
                                @Param("limit") int limit);

    /** 运营风险列表只读取已有 AI 标签，不触发新的模型调用或自动处罚。 */
    @Select("""
            <script>
            select id, conversation_id, sender_id, sequence_no, content, risk_level,
                   risk_categories, risk_evidence, requires_human_review,
                   gmt_create, gmt_modified, is_deleted, version
            from message
            where risk_level in ('MEDIUM', 'HIGH') and is_deleted = false
              and not exists (
                  select 1 from audit_log disposition
                  where disposition.resource_type = 'MESSAGE'
                    and disposition.resource_id = message.id
                    and disposition.action in (
                        'CHAT_RISK_FALSE_POSITIVE', 'CHAT_RISK_REMINDER_RECORDED',
                        'CHAT_RISK_ACCOUNT_RESTRICTED', 'CHAT_RISK_ACCOUNT_DISABLED')
                    and disposition.is_deleted = false)
              <if test="cursor != null">and id &lt; #{cursor}</if>
            order by id desc
            limit #{limit}
            </script>
            """)
    List<MessageDO> selectRiskPage(@Param("cursor") Long cursor, @Param("limit") int limit);
}
