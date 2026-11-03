package com.lotflow.service;

import com.lotflow.common.CursorPageVO;
import com.lotflow.vo.BidHistoryVO;
import com.lotflow.vo.ConversationSummaryVO;
import com.lotflow.vo.DisputeDetailVO;
import com.lotflow.vo.DisputeVO;
import com.lotflow.vo.ListingVO;
import com.lotflow.vo.OrderSummaryVO;
import com.lotflow.vo.TodoItemVO;

import java.util.List;

/** 个人工作台的只读聚合查询。 */
public interface PersonalWorkspaceService {

    CursorPageVO<OrderSummaryVO> orders(long userId, String cursor, String role,
                                        String status, int limit);

    CursorPageVO<ListingVO> sellerListings(long sellerId, String cursor,
                                           String status, int limit);

    CursorPageVO<BidHistoryVO> bidHistory(long bidderId, String cursor, int limit);

    List<TodoItemVO> todos(long userId);

    CursorPageVO<ConversationSummaryVO> conversations(long userId, String cursor, int limit);

    CursorPageVO<DisputeVO> disputes(long userId, boolean privileged, String cursor, int limit);

    DisputeDetailVO dispute(long userId, boolean privileged, long orderId);
}
