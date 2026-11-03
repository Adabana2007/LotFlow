package com.lotflow.vo;

import java.util.List;

/** 管理员审核详情：不受商品公开状态限制的商品快照和图片证据。 */
public record AdminListingDetailVO(ListingVO listing, List<AdminListingImageVO> images) {
}
