package com.lotflow.entity;

import lombok.Getter;
import lombok.Setter;

/** 商品 JSON 快照中的图片元数据，不作为独立数据表持久化。 */
@Getter
@Setter
public class ListingImageDO {
    private Long id;
    private Long listingId;
    private String objectKey;
    private String contentType;
    private Long sizeBytes;
    private Integer sortOrder;
    // 保存确认时的 SHA-256，读取时复算并阻止文件在落盘后被替换。
    private String etag;
}
