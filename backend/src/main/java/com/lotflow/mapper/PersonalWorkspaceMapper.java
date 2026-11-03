package com.lotflow.mapper;

import com.lotflow.entity.ListingDO;
import com.lotflow.vo.BidHistoryVO;
import com.lotflow.vo.ConversationSummaryVO;
import com.lotflow.vo.DisputeVO;
import com.lotflow.vo.OrderSummaryVO;
import com.lotflow.vo.TodoItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 个人工作台专用只读查询，不承担交易命令或状态更新。 */
@Mapper
public interface PersonalWorkspaceMapper {

    List<OrderSummaryVO> selectOrders(@Param("userId") long userId,
                                      @Param("role") String role,
                                      @Param("status") String status,
                                      @Param("cursor") Long cursor,
                                      @Param("limit") int limit);

    List<ListingDO> selectSellerListings(@Param("sellerId") long sellerId,
                                         @Param("status") String status,
                                         @Param("cursor") Long cursor,
                                         @Param("limit") int limit);

    List<BidHistoryVO> selectBidHistory(@Param("bidderId") long bidderId,
                                        @Param("cursor") Long cursor,
                                        @Param("limit") int limit);

    List<TodoItemVO> selectTodos(@Param("userId") long userId);

    List<ConversationSummaryVO> selectConversations(@Param("userId") long userId,
                                                    @Param("cursor") Long cursor,
                                                    @Param("limit") int limit);

    List<DisputeVO> selectDisputes(@Param("userId") long userId,
                                   @Param("privileged") boolean privileged,
                                   @Param("cursor") Long cursor,
                                   @Param("limit") int limit);

    DisputeVO selectDispute(@Param("userId") long userId,
                            @Param("privileged") boolean privileged,
                            @Param("orderId") long orderId);
}
