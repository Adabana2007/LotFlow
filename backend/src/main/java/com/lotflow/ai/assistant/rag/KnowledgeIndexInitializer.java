package com.lotflow.ai.assistant.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 应用可用后在独立 AI 线程池中检查并构建规则索引。 */
@Component
@RequiredArgsConstructor
public class KnowledgeIndexInitializer {

    private final KnowledgeIndexService knowledgeIndexService;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        // 启动后异步执行，避免 Milvus 或 Embedding 超时阻塞 Web 服务就绪。
        knowledgeIndexService.rebuildAsync(false);
    }
}
