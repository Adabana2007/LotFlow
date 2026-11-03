package com.lotflow.ai.assistant.rag;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/** 当前进程可见的知识库索引版本和最近失败原因。 */
@Component
public class KnowledgeIndexState {

    private final AtomicReference<String> activeVersion = new AtomicReference<>();
    private volatile String lastError;

    public boolean isReady() {
        return activeVersion.get() != null;
    }

    public String activeVersion() {
        return activeVersion.get();
    }

    public String lastError() {
        return lastError;
    }

    public void markReady(String version) {
        activeVersion.set(version);
        lastError = null;
    }

    public void markFailed(String error) {
        // 新版本构建失败时保留上一活动版本，用户仍可查询上一版已发布规则。
        lastError = error;
    }

    public void markUnavailable(String error) {
        activeVersion.set(null);
        lastError = error;
    }
}
