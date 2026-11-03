package com.lotflow.service;

import com.lotflow.dto.DisputeDTO;
import com.lotflow.dto.ShipmentDTO;
import com.lotflow.entity.AuctionDO;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.OrderDO;
import com.lotflow.vo.OrderVO;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** 订单支付、担保、履约和超时处理。 */
public interface OrderService {

    OrderDO createAuctionOrder(AuctionDO auction, ListingDO listing,
                               BigDecimal buyerDeposit, OffsetDateTime now);

    OrderVO pay(long buyerId, long orderId, String rawIdempotencyKey);

    OrderVO ship(long sellerId, long orderId, ShipmentDTO request);

    OrderVO confirmReceipt(long buyerId, long orderId);

    OrderVO openDispute(long buyerId, long orderId, DisputeDTO request);

    OrderDO createRetailOrder(ListingDO listing, long buyerId, BigDecimal amount,
                              String sourceType, OffsetDateTime paymentDeadline);

    OrderDO createSeckillOrder(ListingDO listing, long buyerId, BigDecimal amount,
                               OffsetDateTime paymentDeadline, String reservationKey);

    void processPendingPayment(long orderId);

    OrderVO offerSecondChance(long sellerId, long defaultedOrderId);

    void autoConfirmReceipt(long orderId);

    void processSellerShipTimeout(long orderId);

    void completeAfterDisputeWindow(long orderId);

    OrderVO get(long userId, boolean privileged, long orderId);

    List<OrderVO> listMine(long userId);
}
