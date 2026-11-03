package com.lotflow.ai.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotflow.ai.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 百炼重排客户端。 */
@Component
@Slf4j
public class DashScopeRerankClient {

    private final AiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DashScopeRerankClient(AiProperties properties, RestClient.Builder builder,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public List<Integer> rerank(String query, List<String> documents) {
        if (properties.rerankUrl() == null || properties.rerankUrl().isBlank()
                || !properties.embeddingConfigured() || documents.isEmpty()) {
            // 重排不是交易必需能力；未配置或无候选时返回空结果，搜索继续使用已有 RRF 排名。
            return List.of();
        }
        try {
            String body = restClient.post()
                    .uri(properties.rerankUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.dashscopeKey())
                    .body(Map.of("model", properties.rerankModel(),
                            "input", Map.of("query", query, "documents", documents),
                            "parameters", Map.of("return_documents", false)))
                    .retrieve()
                    .body(String.class);
            JsonNode results = objectMapper.readTree(body).path("output").path("results");
            List<Integer> order = new ArrayList<>();
            results.forEach(item -> order.add(item.path("index").asInt()));
            return order;
        } catch (Exception exception) {
            // 远程重排失败只记录告警并回退原顺序，不能让模型供应商故障中断商品搜索。
            log.warn("rerank unavailable, reason={}", exception.getClass().getSimpleName());
            return List.of();
        }
    }
}
