package com.lotflow.controller;

import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import com.lotflow.service.MediaContent;
import com.lotflow.service.MediaStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;

/** 本地商品图片的短时票据直传和读取入口。 */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaStorageService mediaStorageService;

    @PutMapping("/uploads/{ticket}")
    public ResponseEntity<Void> upload(@PathVariable String ticket,
                                       @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false)
                                       String contentType,
                                       HttpServletRequest request) {
        // 上传地址本身是短时一次性能力，因此不要求浏览器把 Access Token 放进资源 URL。
        try (InputStream inputStream = request.getInputStream()) {
            mediaStorageService.storeUpload(ticket, contentType, request.getContentLengthLong(), inputStream);
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "图片上传中断", exception);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/images/{ticket}")
    public ResponseEntity<Resource> image(@PathVariable String ticket) {
        MediaContent content = mediaStorageService.openContent(ticket);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.sizeBytes())
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(content.path()));
    }

    @GetMapping("/avatars/{userId}")
    public ResponseEntity<Resource> avatar(@PathVariable long userId) {
        MediaContent content = mediaStorageService.openAvatar(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.sizeBytes())
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(content.path()));
    }
}
