package com.lotflow.service;

import com.lotflow.vo.OrderVO;

/** 商城一口价下单。 */
public interface ShopService {

    OrderVO purchase(long buyerId, long listingId, String rawIdempotencyKey);
}
