package com.lotflow.service.impl;

import com.lotflow.common.BusinessException;
import com.lotflow.common.CursorPageVO;
import com.lotflow.common.ResultCode;
import com.lotflow.entity.AssistantConversationDO;
import com.lotflow.mapper.AssistantConversationMapper;
import com.lotflow.service.AssistantConversationService;
import com.lotflow.vo.AssistantConversationVO;
import com.lotflow.vo.AssistantMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 助手会话归属校验、列表分页和历史回放。 */
@Service
@RequiredArgsConstructor
public class AssistantConversationServiceImpl implements AssistantConversationService {

    private static final String DEFAULT_TITLE = "新会话";
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int TITLE_FROM_QUESTION_LENGTH = 40;

    private final AssistantConversationMapper mapper;
    private final ChatMemory chatMemory;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AssistantConversationVO create(long userId, String title) {
        AssistantConversationDO conversation = new AssistantConversationDO();
        conversation.setUserId(userId);
        conversation.setTitle(normalizeTitle(title));
        conversation.setLastMessageAt(OffsetDateTime.now(ZoneOffset.UTC));
        mapper.insert(conversation);
        return AssistantConversationVO.from(conversation);
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<AssistantConversationVO> list(long userId, String cursor, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        Cursor parsed = parseCursor(cursor);
        List<AssistantConversationDO> rows = mapper.selectPage(userId,
                parsed == null ? null : parsed.timestamp(),
                parsed == null ? null : parsed.id(), limit + 1);
        boolean hasMore = rows.size() > limit;
        List<AssistantConversationDO> selected = hasMore ? rows.subList(0, limit) : rows;
        String next = hasMore ? nextCursor(selected.getLast()) : null;
        return new CursorPageVO<>(selected.stream().map(AssistantConversationVO::from).toList(), next);
    }

    @Transactional(readOnly = true)
    @Override
    public List<AssistantMessageVO> messages(long userId, long conversationId) {
        requireOwned(userId, conversationId);
        return chatMemory.get(AssistantConversationService.memoryId(conversationId)).stream()
                .filter(message -> message.getMessageType() == MessageType.USER
                        || message.getMessageType() == MessageType.ASSISTANT)
                .map(message -> new AssistantMessageVO(
                        message.getMessageType() == MessageType.USER ? "user" : "assistant",
                        message.getText()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(long userId, long conversationId) {
        requireOwned(userId, conversationId);
        // 会话软删只影响列表，消息必须物理清除，否则同一 ID 再次提问会带出旧上下文。
        chatMemory.clear(AssistantConversationService.memoryId(conversationId));
        mapper.deleteById(conversationId);
    }

    @Transactional(readOnly = true)
    @Override
    public void requireOwned(long userId, long conversationId) {
        AssistantConversationDO conversation = mapper.selectActive(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            // 不区分"不存在"和"属于他人"，避免通过会话 ID 探测其他用户的对话。
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void touch(long conversationId) {
        mapper.touch(conversationId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void renameIfDefault(long userId, long conversationId, String question) {
        AssistantConversationDO conversation = mapper.selectActive(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)
                || !DEFAULT_TITLE.equals(conversation.getTitle())) {
            return;
        }
        mapper.updateTitle(conversationId, truncate(question.trim(), TITLE_FROM_QUESTION_LENGTH));
    }

    private String normalizeTitle(String title) {
        String value = title == null ? "" : title.trim();
        return value.isEmpty() ? DEFAULT_TITLE : truncate(value, MAX_TITLE_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String nextCursor(AssistantConversationDO conversation) {
        return conversation.getLastMessageAt().toInstant().toEpochMilli() + ":" + conversation.getId();
    }

    private Cursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        String[] parts = cursor.split(":", 2);
        try {
            return new Cursor(OffsetDateTime.ofInstant(
                    Instant.ofEpochMilli(Long.parseLong(parts[0])), ZoneOffset.UTC),
                    Long.parseLong(parts[1]));
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException exception) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "游标格式不合法");
        }
    }

    private record Cursor(OffsetDateTime timestamp, long id) {
    }
}
