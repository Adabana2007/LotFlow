package com.lotflow.service;

import com.lotflow.dto.ListingImageDTO;
import com.lotflow.dto.MediaUploadDTO;
import com.lotflow.dto.AvatarRegistrationDTO;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.ListingImageDO;
import com.lotflow.vo.ImageUrlVO;
import com.lotflow.vo.MediaUploadVO;
import org.springframework.ai.content.Media;

import java.io.InputStream;

/** 商品图片上传、校验和读取。 */
public interface MediaStorageService {

    /** 公开拍品的封面短时地址；非公开状态、无图或媒体异常时返回 null。 */
    String coverUrl(ListingDO listing);

    MediaUploadVO createUpload(long sellerId, long listingId, MediaUploadDTO request);

    MediaUploadVO createAvatarUpload(long userId, MediaUploadDTO request);

    String validateAvatar(long userId, AvatarRegistrationDTO request);

    MediaContent openAvatar(long userId);

    void storeUpload(String ticket, String contentType, long contentLength, InputStream inputStream);

    String validateUploaded(long sellerId, long listingId, ListingImageDTO request);

    ImageUrlVO readUrl(Long viewerId, long listingId, long imageId);

    /** 审核专用读取地址：管理员核对实拍图，不做卖家归属校验。 */
    ImageUrlVO readUrlForReview(long listingId, long imageId);

    MediaContent openContent(String ticket);

    Media mediaFor(ListingImageDO image);

    boolean isPublicEndpointReachableByModel();
}
