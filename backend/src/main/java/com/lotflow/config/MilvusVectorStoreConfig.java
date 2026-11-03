package com.lotflow.config;

import com.lotflow.ai.assistant.rag.RagProperties;
import io.micrometer.observation.ObservationRegistry;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** 商品与平台规则使用相互隔离的 Milvus Collection。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "milvus")
@ConditionalOnExpression("'${lotflow.ai.dashscope-key:}' != ''")
public class MilvusVectorStoreConfig {

    private static final String DATABASE_NAME = "default";
    private static final String LISTING_COLLECTION = "listing_embeddings";
    private static final int EMBEDDING_DIMENSIONS = 1024;

    @Bean("listingVectorStore")
    @Lazy
    public VectorStore listingVectorStore(
            MilvusServiceClient milvusClient,
            @Qualifier("dashScopeEmbeddingModel") EmbeddingModel embeddingModel,
            BatchingStrategy batchingStrategy,
            ObservationRegistry observationRegistry) {
        return vectorStore(milvusClient, embeddingModel, batchingStrategy,
                observationRegistry, LISTING_COLLECTION);
    }

    @Bean("policyVectorStore")
    @Lazy
    public VectorStore policyVectorStore(
            MilvusServiceClient milvusClient,
            @Qualifier("dashScopeEmbeddingModel") EmbeddingModel embeddingModel,
            BatchingStrategy batchingStrategy,
            ObservationRegistry observationRegistry,
            RagProperties properties) {
        return vectorStore(milvusClient, embeddingModel, batchingStrategy,
                observationRegistry, properties.collectionName());
    }

    private VectorStore vectorStore(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel,
                                    BatchingStrategy batchingStrategy,
                                    ObservationRegistry observationRegistry, String collectionName) {
        // 两个 Collection 生命周期不同：商品随商品状态变化，规则随发布版本变化，分开避免检索串域。
        return MilvusVectorStore.builder(milvusClient, embeddingModel)
                .databaseName(DATABASE_NAME)
                .collectionName(collectionName)
                .embeddingDimension(EMBEDDING_DIMENSIONS)
                .indexType(IndexType.IVF_FLAT)
                .metricType(MetricType.COSINE)
                .batchingStrategy(batchingStrategy)
                .observationRegistry(observationRegistry)
                .initializeSchema(true)
                .build();
    }
}
