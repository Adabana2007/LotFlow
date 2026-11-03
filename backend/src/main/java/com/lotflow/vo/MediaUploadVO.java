package com.lotflow.vo;

import java.time.Instant;
import java.util.Map;

/** 短时有效的后端图片上传票据。 */
public record MediaUploadVO(String objectKey, String uploadUrl, String httpMethod,
                            Map<String, String> headers, Instant expiresAt) {
}
