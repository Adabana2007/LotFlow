package com.lotflow.ai.assistant.rag.impl;

import com.lotflow.ai.assistant.rag.KnowledgeCorpus;
import com.lotflow.ai.assistant.rag.KnowledgeCorpusLoader;
import com.lotflow.ai.assistant.rag.KnowledgeIndexService;
import com.lotflow.ai.assistant.rag.KnowledgeIndexState;
import com.lotflow.ai.assistant.rag.RagProperties;
import com.lotflow.vo.KnowledgeIndexVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** 使用 Spring AI VectorStore 构建版本化平台规则索引。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIndexServiceImpl implements KnowledgeIndexService {

    private static final String KEY_PREFIX = "lotflow:ai:rag:";
    private static final String ACTIVE_VERSION_KEY = KEY_PREFIX + "active-version";
    private static final String INDEX_LOCK_KEY = KEY_PREFIX + "knowledge-index-lock";
    private static final String CORPUS_VERSION_FIELD = "corpusVersion";
    private static final int MAX_ERROR_LENGTH = 300;

    private final KnowledgeCorpusLoader corpusLoader;
    private final RagProperties properties;
    private final KnowledgeIndexState indexState;
    @Qualifier("policyVectorStore")
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final StringRedisTemplate redisTemplate;
    @Qualifier("knowledgeIndexUnlockScript")
    private final DefaultRedisScript<Long> unlockScript;
    @Qualifier("aiTaskExecutor")
    private final TaskExecutor aiTaskExecutor;

    @Override
    public void rebuildAsync(boolean force) {
        if (!properties.enabled()) {
            log.info("平台规则知识库未启用");
            return;
        }
        try {
            aiTaskExecutor.execute(() -> rebuild(force));
        } catch (TaskRejectedException exception) {
            indexState.markFailed("AI 索引线程池繁忙");
            log.warn("平台规则知识库重建任务被拒绝");
        }
    }

    @Override
    public void synchronizeActiveVersion() {
        if (!properties.enabled()) {
            indexState.markUnavailable("平台规则知识库未启用");
            return;
        }
        try {
            String activeVersion = activeVersion();
            if (activeVersion == null) {
                indexState.markUnavailable("平台规则知识库尚未完成索引");
                return;
            }
            indexState.markReady(activeVersion);
        } catch (RuntimeException exception) {
            // Redis 保存跨实例索引版本；无法确认当前版本时宁可让助手降级，也不使用过期规则回答。
            indexState.markUnavailable("无法读取平台规则索引版本");
            log.warn("读取平台规则索引版本失败: {}", compactError(exception));
        }
    }

    @Override
    public KnowledgeIndexVO status() {
        return new KnowledgeIndexVO(indexState.isReady(), indexState.activeVersion(),
                indexState.lastError());
    }

    private void rebuild(boolean force) {
        try {
            KnowledgeCorpus corpus = corpusLoader.load();
            if (!force && publishedVersion().equals(publishedVersion(corpus.version()))) {
                indexState.markReady(corpus.version());
                log.info("平台规则知识库已是最新版本: {}", shortVersion(corpus.version()));
                return;
            }
            rebuildLocked(corpus, force);
        } catch (RuntimeException exception) {
            String error = compactError(exception);
            indexState.markFailed(error);
            log.warn("平台规则知识库索引失败: {}", error);
        }
    }

    private void rebuildLocked(KnowledgeCorpus corpus, boolean force) {
        String lockToken = UUID.randomUUID().toString();
        if (!tryLock(lockToken)) {
            log.info("其他实例正在构建平台规则知识库");
            return;
        }
        try {
            if (!force && publishedVersion().equals(publishedVersion(corpus.version()))) {
                indexState.markReady(corpus.version());
                return;
            }
            VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
            if (vectorStore == null) {
                throw new IllegalStateException("Milvus VectorStore 不可用");
            }
            // 先写入新版本，再发布活动版本；重建期间检索仍使用旧版本，不会混读两套规则。
            vectorStore.add(corpus.documents());
            publishVersion(corpus.version());
            indexState.markReady(corpus.version());
            deleteInactiveVersions(vectorStore, corpus.version());
            log.info("平台规则知识库索引完成: version={}, chunks={}",
                    shortVersion(corpus.version()), corpus.documents().size());
        } finally {
            releaseLock(lockToken);
        }
    }

    private boolean tryLock(String token) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(INDEX_LOCK_KEY, token, properties.indexLockTtl());
        return Boolean.TRUE.equals(acquired);
    }

    private void releaseLock(String token) {
        try {
            redisTemplate.execute(unlockScript, List.of(INDEX_LOCK_KEY), token);
        } catch (RuntimeException exception) {
            log.warn("释放平台规则索引锁失败: {}", compactError(exception));
        }
    }

    private void publishVersion(String version) {
        redisTemplate.opsForValue().set(ACTIVE_VERSION_KEY, publishedVersion(version),
                properties.readyTtl());
    }

    private String activeVersion() {
        String value = redisTemplate.opsForValue().get(ACTIVE_VERSION_KEY);
        String prefix = properties.collectionName() + ":";
        if (value == null || !value.startsWith(prefix)) {
            return null;
        }
        String version = value.substring(prefix.length());
        return version.isBlank() ? null : version;
    }

    private String publishedVersion() {
        String version = activeVersion();
        return version == null ? "" : publishedVersion(version);
    }

    private String publishedVersion(String version) {
        return properties.collectionName() + ":" + version;
    }

    private void deleteInactiveVersions(VectorStore vectorStore, String activeVersion) {
        try {
            FilterExpressionBuilder filters = new FilterExpressionBuilder();
            vectorStore.delete(filters.ne(CORPUS_VERSION_FIELD, activeVersion).build());
        } catch (RuntimeException exception) {
            // 旧版本清理失败不影响版本过滤和安全回答，留待下次重建继续清理。
            log.warn("平台规则知识库旧版本清理失败: {}", compactError(exception));
        }
    }

    private String compactError(RuntimeException exception) {
        String message = exception.getMessage();
        String error = message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    private String shortVersion(String version) {
        return version.substring(0, Math.min(16, version.length()));
    }
}
