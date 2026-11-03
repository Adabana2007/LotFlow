package com.lotflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/** 本地商品图片目录和短时媒体票据配置。 */
@ConfigurationProperties("lotflow.media")
public record MediaStorageProperties(
        String root,
        URI publicBaseUrl,
        Duration uploadTicketTtl,
        Duration readTicketTtl
) {
}
