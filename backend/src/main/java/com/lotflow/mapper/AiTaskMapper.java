package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.AiTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** AI 任务审计和可恢复任务状态持久化。 */
@Mapper
public interface AiTaskMapper extends BaseMapper<AiTaskDO> {

    @Select("""
            <script>
            select id, user_id, listing_id, review_generation, task_type, status,
                   input_summary, output_summary, model_name, prompt_version,
                   prompt_tokens, completion_tokens, latency_ms, trace_id, error_code,
                   gmt_create, gmt_modified, is_deleted, version
            from ai_task
            where is_deleted = false
              <if test="cursor != null">and id &lt; #{cursor}</if>
            order by id desc
            limit #{limit}
            </script>
            """)
    List<AiTaskDO> selectPage(@Param("cursor") Long cursor, @Param("limit") int limit);
}
