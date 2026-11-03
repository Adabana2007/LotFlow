package com.lotflow.controller;

import com.lotflow.common.CursorPageVO;
import com.lotflow.dto.CreateListingDTO;
import com.lotflow.dto.ListingImageDTO;
import com.lotflow.dto.MediaUploadDTO;
import com.lotflow.dto.AuctionScheduleDTO;
import com.lotflow.security.CurrentUser;
import com.lotflow.service.ListingService;
import com.lotflow.service.AuctionScheduleService;
import com.lotflow.service.MediaStorageService;
import com.lotflow.vo.ListingVO;
import com.lotflow.vo.SellerListingImageVO;
import com.lotflow.vo.MediaUploadVO;
import com.lotflow.vo.ImageUrlVO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/** 商品草稿和提审接口。 */
@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;
    private final CurrentUser currentUser;
    private final AuctionScheduleService auctionScheduleService;
    private final MediaStorageService mediaStorageService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> create(@Valid @RequestBody CreateListingDTO request) {
        long id = listingService.createDraft(currentUser.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/listings/" + id)).build();
    }

    @PutMapping("/{listingId}")
    public ResponseEntity<Void> update(@PathVariable long listingId,
                                       @org.springframework.web.bind.annotation.RequestHeader(
                                               value = "If-Match") String ifMatch,
                                       @Valid @RequestBody CreateListingDTO request) {
        listingService.updateDraft(currentUser.id(), listingId, expectedVersion(ifMatch), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{listingId}/images")
    public List<SellerListingImageVO> sellerImages(@PathVariable long listingId) {
        return listingService.listImages(currentUser.id(), listingId);
    }

    @PostMapping("/{listingId}/images")
    public ResponseEntity<Map<String, String>> addImage(@PathVariable long listingId,
                                                       @Valid @RequestBody ListingImageDTO request) {
        // 图片登记前先向对象存储确认实际类型和大小，数据库只保存通过校验的图片元数据。
        String etag = mediaStorageService.validateUploaded(currentUser.id(), listingId, request);
        long id = listingService.addImage(currentUser.id(), listingId, request, etag);
        return ResponseEntity.created(URI.create("/api/v1/listings/" + listingId + "/images/" + id))
                .body(Map.of("id", Long.toString(id)));
    }

    @PostMapping("/{listingId}/image-uploads")
    public ResponseEntity<MediaUploadVO> imageUpload(@PathVariable long listingId,
                                                     @Valid @RequestBody MediaUploadDTO request) {
        // 上传地址绑定商品和短时票据；二进制仍与 JSON 分开传输，登记时再核对实际文件。
        return ResponseEntity.ok(mediaStorageService.createUpload(currentUser.id(), listingId, request));
    }

    @GetMapping("/{listingId}/images/{imageId}/url")
    public ImageUrlVO imageUrl(@PathVariable long listingId, @PathVariable long imageId) {
        // 游客只能读取公开渠道图片；草稿图片需要 optionalId 对应的卖家身份。
        return mediaStorageService.readUrl(currentUser.optionalId().orElse(null), listingId, imageId);
    }

    @PostMapping("/{listingId}/submissions")
    public ResponseEntity<Map<String, String>> submit(@PathVariable long listingId) {
        long taskId = listingService.submit(currentUser.id(), listingId);
        // 提交只创建异步审核任务，所以返回 202 和 reviewTaskId，而不是直接返回审核结论。
        return ResponseEntity.accepted().body(Map.of("reviewTaskId", Long.toString(taskId)));
    }

    @PostMapping("/{listingId}/auction-schedules")
    public ResponseEntity<Map<String, String>> schedule(
            @PathVariable long listingId,
            @org.springframework.web.bind.annotation.RequestBody(required = false)
            @Valid AuctionScheduleDTO request) {
        long auctionId = auctionScheduleService.schedule(currentUser.id(), listingId, request);
        // 排期成功会创建拍卖并冻结卖家保证金，返回 201 和新拍卖资源 ID。
        return ResponseEntity.status(201).body(Map.of("auctionId", Long.toString(auctionId)));
    }

    @GetMapping
    public CursorPageVO<ListingVO> list(@RequestParam(required = false) String cursor,
                                        @RequestParam(defaultValue = "20") int limit) {
        return listingService.listVisible(cursor, limit);
    }

    @GetMapping("/mine")
    public CursorPageVO<ListingVO> mineList(@RequestParam(required = false) String cursor,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "50") int limit) {
        return listingService.pageMine(currentUser.id(), cursor, status, limit);
    }

    @GetMapping("/{listingId}")
    public ListingVO detail(@PathVariable long listingId) {
        return listingService.getVisible(listingId);
    }

    @GetMapping("/{listingId}/mine")
    public ListingVO mine(@PathVariable long listingId) {
        // 卖家详情允许查看草稿和审核失败原因，因此走归属校验而不是公开详情查询。
        return listingService.getForSeller(currentUser.id(), listingId);
    }

    private long expectedVersion(String ifMatch) {
        String value = ifMatch == null ? "" : ifMatch.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new com.lotflow.common.BusinessException(
                    com.lotflow.common.ResultCode.INVALID_ARGUMENT,
                    "If-Match 必须包含商品版本号");
        }
    }
}
