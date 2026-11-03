package com.lotflow.service;

import com.lotflow.common.CursorPageVO;
import com.lotflow.dto.PlaceBidDTO;
import com.lotflow.entity.ListingDO;
import com.lotflow.vo.AuctionVO;
import com.lotflow.vo.BidVO;

/** 拍卖创建、出价、结束和取消流程。 */
public interface AuctionService {

    long createScheduledAuction(ListingDO listing);

    BidVO placeBid(long bidderId, long auctionId, PlaceBidDTO request, String rawIdempotencyKey);

    void processAuction(long auctionId);

    void cancel(long sellerId, long auctionId);

    AuctionVO get(long auctionId);

    /** activeOnly 为 true 时只返回 SCHEDULED/LIVE，用于拍卖大厅；商品详情需要包含历史场次。 */
    CursorPageVO<AuctionVO> list(String cursor, int requestedLimit, boolean activeOnly);

    CursorPageVO<BidVO> bids(long auctionId, String cursor, int requestedLimit);
}
