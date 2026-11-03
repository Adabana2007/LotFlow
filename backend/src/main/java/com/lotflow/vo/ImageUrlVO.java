package com.lotflow.vo;

import java.time.Instant;

/** 短时有效的图片读取地址。 */
public record ImageUrlVO(String url, Instant expiresAt) {
}
