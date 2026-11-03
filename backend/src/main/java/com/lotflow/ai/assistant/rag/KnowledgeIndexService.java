package com.lotflow.ai.assistant.rag;

import com.lotflow.vo.KnowledgeIndexVO;

/** 平台规则知识库的后台构建、版本同步和状态查询。 */
public interface KnowledgeIndexService {

    void rebuildAsync(boolean force);

    void synchronizeActiveVersion();

    KnowledgeIndexVO status();
}
