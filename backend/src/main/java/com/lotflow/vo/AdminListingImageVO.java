package com.lotflow.vo;

import java.time.Instant;

/** 审核视图中的单张图片证据，读取地址短时有效。 */
public record AdminListingImageVO(String id, String contentType, long sizeBytes,
                                  int sortOrder, String url, Instant expiresAt) {
}
