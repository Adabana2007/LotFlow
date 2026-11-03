package com.lotflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotflow.entity.ConversationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 会话、双方已读位置和消息序号持久化。 */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationDO> {

    @Select("""
            select id, listing_id, buyer_id, seller_id, status, last_sequence,
                   buyer_last_read_sequence, seller_last_read_sequence,
                   gmt_create, gmt_modified, is_deleted, version
            from conversation
            where id = #{conversationId} and is_deleted = false
            for update
            """)
    ConversationDO selectByIdForUpdate(@Param("conversationId") long conversationId);

    @Select("""
            select id, listing_id, buyer_id, seller_id, status, last_sequence,
                   buyer_last_read_sequence, seller_last_read_sequence,
                   gmt_create, gmt_modified, is_deleted, version
            from conversation
            where buyer_id = #{buyerId} and listing_id = #{listingId} and is_deleted = false
            """)
    ConversationDO selectByBuyerAndListing(@Param("buyerId") long buyerId,
                                           @Param("listingId") long listingId);
}
