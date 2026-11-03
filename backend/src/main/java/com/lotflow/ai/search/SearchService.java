package com.lotflow.ai.search;

import com.lotflow.ai.AiProperties;
import com.lotflow.ai.gateway.DashScopeRerankClient;
import com.lotflow.common.Cursor;
import com.lotflow.common.CursorPageVO;
import com.lotflow.entity.ListingDO;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.service.MediaStorageService;
import com.lotflow.vo.ListingVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 商品关键词、向量召回和重排搜索。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    /** 向量候选需要大于最终页大小：可见性过滤和重排在召回之后仍会丢弃部分命中。 */
    private static final int VECTOR_TOP_K = 50;
    private static final List<String> VISIBLE_STATUSES =
            List.of("SCHEDULED", "AUCTION_LIVE", "SECKILL", "SHOP");

    private final ListingMapper listingMapper;
    @Qualifier("listingVectorStore")
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final DashScopeRerankClient rerankClient;
    private final AiProperties properties;
    private final MediaStorageService mediaStorageService;

    public CursorPageVO<ListingVO> search(String query, String cursor, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        List<ListingDO> keywordRows = listingMapper.searchVisiblePage(query, Cursor.parse(cursor), limit);
        List<ListingDO> results = keywordRows;
        String next = keywordRows.isEmpty() ? null : keywordRows.getLast().getId().toString();
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        // 普通关键词和结构化过滤是交易可用性底线，向量召回只在 Embedding 和 VectorStore 都可用时叠加。
        if (properties.embeddingConfigured() && vectorStore != null) {
            try {
                List<Document> vectorDocs = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(query)
                        .topK(VECTOR_TOP_K)
                        .similarityThreshold(0.35)
                        .filterExpression(visibleStatusFilter())
                        .build());
                results = fuse(query, keywordRows, vectorDocs, limit);
                next = null;
            } catch (RuntimeException exception) {
                // Milvus 或重排故障不能阻断搜索：记录可诊断原因后继续使用 PostgreSQL 关键词结果。
                log.warn("hybrid search degraded to keyword only, reason={}", reason(exception));
            }
        }
        return new CursorPageVO<>(results.stream()
                .map(row -> ListingVO.from(row, mediaStorageService.coverUrl(row)))
                .toList(), next);
    }

    private Filter.Expression visibleStatusFilter() {
        // 索引 metadata 的 status 是写入时快照，售出或退回仍由回表查询按当前状态兜底。
        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        return filters.in("status", VISIBLE_STATUSES.toArray()).build();
    }

    private List<ListingDO> fuse(String query, List<ListingDO> keywordRows,
                                 List<Document> vectorDocs, int limit) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        // RRF 只依赖名次而不依赖两路分数量纲，避免拼接不同检索器的原始分数。
        for (int i = 0; i < keywordRows.size(); i++) {
            scores.merge(keywordRows.get(i).getId(), 1.0 / (60 + i + 1), Double::sum);
        }
        for (int i = 0; i < vectorDocs.size(); i++) {
            Object value = vectorDocs.get(i).getMetadata().get("listingId");
            if (value != null) {
                scores.merge(Long.parseLong(value.toString()), 1.0 / (60 + i + 1), Double::sum);
            }
        }
        List<Long> ids = scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(Math.max(limit * 2L, 20L))
                .map(Map.Entry::getKey).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        // RRF 只返回候选 ID，最终仍回表按可见状态过滤，防止向量库召回已下架或审核中的商品。
        List<ListingDO> loaded = listingMapper.selectVisibleByIds(ids);
        Map<Long, ListingDO> byId = new LinkedHashMap<>();
        loaded.forEach(item -> byId.put(item.getId(), item));
        List<ListingDO> baseRanked = ids.stream().map(byId::get)
                .filter(java.util.Objects::nonNull).toList();
        List<Integer> reranked = rerankClient.rerank(query,
                baseRanked.stream().map(item -> item.getTitle() + " " + item.getConditionCode()).toList());
        List<ListingDO> ranked = baseRanked;
        if (!reranked.isEmpty()) {
            List<ListingDO> ordered = new ArrayList<>();
            reranked.stream().filter(index -> index >= 0 && index < baseRanked.size())
                    .forEach(index -> ordered.add(baseRanked.get(index)));
            ranked = ordered;
        }
        return ranked.stream().limit(limit).toList();
    }

    private String reason(RuntimeException exception) {
        // 只记录异常类型和截断信息，不输出检索词，避免把用户查询写入日志。
        String reason = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return reason.length() > 200 ? reason.substring(0, 200) : reason;
    }
}
