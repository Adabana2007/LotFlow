package com.lotflow.service.impl;

import com.lotflow.service.AuctionScheduleService;
import com.lotflow.service.AuctionService;

import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.UserDO;
import com.lotflow.entity.enums.DomainEnums.ListingStatus;
import com.lotflow.dto.AuctionScheduleDTO;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 审核通过商品的拍卖排期激活。 */
@Service
@RequiredArgsConstructor
public class AuctionScheduleServiceImpl implements AuctionScheduleService {

    private final ListingMapper listingMapper;
    private final UserMapper userMapper;
    private final AuctionService auctionService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public long schedule(long sellerId, long listingId, AuctionScheduleDTO request) {
        // 排期会冻结卖家保证金并创建拍卖，先锁商品并校验 sellerId/APPROVED，避免重复或越权激活。
        ListingDO listing = listingMapper.selectByIdForUpdate(listingId);
        if (listing == null || !listing.getSellerId().equals(sellerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权激活该商品排期");
        }
        if (listing.getStatus() != ListingStatus.APPROVED) {
            throw new BusinessException(ResultCode.CONFLICT, "商品当前不能激活拍卖排期");
        }
        if (request != null) {
            listing.setScheduledStart(request.scheduledStart());
            listingMapper.updateById(listing);
        }
        UserDO seller = userMapper.selectById(sellerId);
        if (seller.getCreditScore() < 60) {
            // 信用分低于 60 时平台暂停创建新拍卖，校验必须在冻结保证金之前完成。
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION,
                    "信用分低于 60，暂停创建新拍卖");
        }
        return auctionService.createScheduledAuction(listing);
    }
}
