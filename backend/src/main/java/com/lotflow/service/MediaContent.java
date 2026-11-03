package com.lotflow.service;

import java.nio.file.Path;

/** 已通过短时票据授权的本地图片内容。 */
public record MediaContent(Path path, String contentType, long sizeBytes) {
}
