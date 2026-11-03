package com.lotflow.service;

import com.lotflow.common.CursorPageVO;
import com.lotflow.dto.CreateListingDTO;
import com.lotflow.dto.ListingImageDTO;
import com.lotflow.vo.AdminListingDetailVO;
import com.lotflow.vo.ListingVO;
import com.lotflow.vo.SellerListingImageVO;

import java.util.List;

/** 商品从草稿、图片登记到提交审核的业务状态编排。 */
public interface ListingService {

    long createDraft(long sellerId, CreateListingDTO request);

    long addImage(long sellerId, long listingId, ListingImageDTO request, String etag);

    void updateDraft(long sellerId, long listingId, long expectedVersion,
                     CreateListingDTO request);

    List<SellerListingImageVO> listImages(long sellerId, long listingId);

    long submit(long sellerId, long listingId);

    CursorPageVO<ListingVO> listVisible(String cursor, int requestedLimit);

    ListingVO getVisible(long listingId);

    ListingVO getForSeller(long sellerId, long listingId);

    /** 审核详情：不限制商品状态，图片以短时地址返回，只允许后台入口调用。 */
    AdminListingDetailVO getForReview(long listingId);

    List<ListingVO> listMine(long sellerId);

    CursorPageVO<ListingVO> pageMine(long sellerId, String cursor, String status, int limit);
}
