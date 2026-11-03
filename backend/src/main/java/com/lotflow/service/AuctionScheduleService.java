package com.lotflow.service;

import com.lotflow.dto.AuctionScheduleDTO;

/** 审核通过商品的拍卖排期激活。 */
public interface AuctionScheduleService {

    long schedule(long sellerId, long listingId, AuctionScheduleDTO request);
}
