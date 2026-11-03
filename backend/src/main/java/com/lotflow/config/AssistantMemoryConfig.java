package com.lotflow.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 平台助手对话记忆的窗口策略。 */
@Configuration
public class AssistantMemoryConfig {

    /** 20 条约等于 10 轮问答；窗口按整轮淘汰并始终保留 SystemMessage。 */
    private static final int MAX_MESSAGES = 20;

    @Bean
    public ChatMemory assistantChatMemory(ChatMemoryRepository repository) {
        // 存储交给官方 ChatMemoryRepository（PostgreSQL），这里只决定保留多少轮对话。
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(MAX_MESSAGES)
                .build();
    }
}
