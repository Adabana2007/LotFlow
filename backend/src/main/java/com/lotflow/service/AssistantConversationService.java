package com.lotflow.service;

import com.lotflow.common.CursorPageVO;
import com.lotflow.vo.AssistantConversationVO;
import com.lotflow.vo.AssistantMessageVO;

import java.util.List;

/** 助手会话归属、列表和记忆读取。 */
public interface AssistantConversationService {

    /** 记忆键统一加前缀，避免与其他使用 ChatMemory 的能力串号。 */
    static String memoryId(long conversationId) {
        return "assistant:" + conversationId;
    }

    AssistantConversationVO create(long userId, String title);

    CursorPageVO<AssistantConversationVO> list(long userId, String cursor, int requestedLimit);

    List<AssistantMessageVO> messages(long userId, long conversationId);

    void delete(long userId, long conversationId);

    /** 校验会话归属；不属于当前用户或已删除时统一按不存在处理。 */
    void requireOwned(long userId, long conversationId);

    void touch(long conversationId);

    /** 新会话首次提问后用问题前 40 字替换默认标题。 */
    void renameIfDefault(long userId, long conversationId, String question);
}
