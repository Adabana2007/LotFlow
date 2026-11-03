package com.lotflow.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI 能力开关和外置服务配置。 */
@ConfigurationProperties("lotflow.ai")
public record AiProperties(
        String deepseekKey,
        String dashscopeKey,
        String deepseekBaseUrl,
        String deepseekModel,
        String dashscopeBaseUrl,
        String embeddingModel,
        String rerankUrl,
        String rerankModel
) {
    public boolean chatConfigured() {
        return deepseekKey != null && !deepseekKey.isBlank();
    }

    public boolean embeddingConfigured() {
        return dashscopeKey != null && !dashscopeKey.isBlank();
    }
}
