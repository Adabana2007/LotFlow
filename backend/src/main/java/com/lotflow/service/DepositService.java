package com.lotflow.service;

import com.lotflow.entity.OrderDO;

/** 保证金状态和资金流水联动。 */
public interface DepositService {

    void moveBuyerDepositToSeller(OrderDO order);

    void releaseSellerDeposit(Long auctionId);
}
