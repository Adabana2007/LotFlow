package com.lotflow.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

/** 按模型配置的 AI Token 单价；未配置时不生成推测成本。 */
@ConfigurationProperties("lotflow.ai.costs")
public record AiCostProperties(Map<String, ModelRate> models) {

    public ModelRate rateFor(String modelName) {
        return models == null || modelName == null ? null : models.get(modelName);
    }

    public record ModelRate(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {
        public boolean isConfigured() {
            return inputPerMillion != null && inputPerMillion.signum() > 0
                    && outputPerMillion != null && outputPerMillion.signum() > 0;
        }
    }
}
