package com.lotflow.vo;

import java.time.Instant;

/** 卖家编辑页读取的图片元数据和短时展示地址。 */
public record SellerListingImageVO(String id, int sortOrder, long sizeBytes,
                                   String contentType, String url, Instant expiresAt) {
}
