package com.lotflow.service;

import com.lotflow.vo.OrderVO;

/** 秒杀库存预占、订单创建和超时释放。 */
public interface SeckillService {

    OrderVO purchase(long buyerId, long listingId, String rawIdempotencyKey);

    /** 秒杀订单超时后释放数据库库存和 Redis 预占令牌；非秒杀订单或已完成订单直接返回。 */
    void expireReservation(long orderId);
}
