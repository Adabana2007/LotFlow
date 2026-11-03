package com.lotflow.service.impl;

import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import com.lotflow.config.MediaStorageProperties;
import com.lotflow.dto.ListingImageDTO;
import com.lotflow.dto.MediaUploadDTO;
import com.lotflow.dto.AvatarRegistrationDTO;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.ListingImageDO;
import com.lotflow.entity.UserDO;
import com.lotflow.entity.enums.DomainEnums.ListingStatus;
import com.lotflow.mapper.ListingMapper;
import com.lotflow.mapper.UserMapper;
import com.lotflow.service.MediaContent;
import com.lotflow.service.MediaStorageService;
import com.lotflow.vo.ImageUrlVO;
import com.lotflow.vo.MediaUploadVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.content.Media;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** 本地商品图片上传、校验和短时票据读取。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MediaStorageServiceImpl implements MediaStorageService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final int BUFFER_BYTES = 8192;
    private static final String UPLOAD_KEY_PREFIX = "lotflow:media:upload:";
    private static final String READ_KEY_PREFIX = "lotflow:media:read:";
    private static final Pattern IMAGE_FILE_NAME = Pattern.compile(
            "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(?:jpg|png|webp)");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");
    private static final Set<ListingStatus> PUBLIC_LISTING_STATUSES = Set.of(
            ListingStatus.SCHEDULED,
            ListingStatus.AUCTION_LIVE,
            ListingStatus.SECKILL,
            ListingStatus.SHOP);

    private final MediaStorageProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ListingMapper listingMapper;
    private final UserMapper userMapper;
    private final ListingImageJsonCodec imageCodec;

    @Override
    public MediaUploadVO createUpload(long sellerId, long listingId, MediaUploadDTO request) {
        ListingDO listing = requireEditableListing(sellerId, listingId);
        String contentType = normalizeContentType(request.contentType());
        String extension = EXTENSIONS.get(contentType);
        if (extension == null || request.sizeBytes() <= 0 || request.sizeBytes() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "图片类型或大小不合法");
        }
        String objectKey = listingObjectPrefix(sellerId, listing.getId())
                + UUID.randomUUID() + "." + extension;
        String ticket = issueUploadTicket(objectKey);
        Instant expiresAt = Instant.now().plus(properties.uploadTicketTtl());
        return new MediaUploadVO(objectKey, publicUrl("/api/v1/media/uploads/" + ticket), "PUT",
                Map.of("Content-Type", contentType), expiresAt);
    }

    @Override
    public MediaUploadVO createAvatarUpload(long userId, MediaUploadDTO request) {
        String contentType = normalizeContentType(request.contentType());
        String extension = EXTENSIONS.get(contentType);
        if (extension == null || request.sizeBytes() <= 0 || request.sizeBytes() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "图片类型或大小不合法");
        }
        String objectKey = avatarObjectPrefix(userId) + UUID.randomUUID() + "." + extension;
        String ticket = issueUploadTicket(objectKey);
        Instant expiresAt = Instant.now().plus(properties.uploadTicketTtl());
        return new MediaUploadVO(objectKey, publicUrl("/api/v1/media/uploads/" + ticket), "PUT",
                Map.of("Content-Type", contentType), expiresAt);
    }

    @Override
    public void storeUpload(String ticket, String contentType, long contentLength, InputStream inputStream) {
        String normalizedType = normalizeContentType(contentType);
        String extension = EXTENSIONS.get(normalizedType);
        if (extension == null || contentLength == 0) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "图片类型或大小不合法");
        }
        if (contentLength > MAX_IMAGE_BYTES) {
            throw new BusinessException(ResultCode.PAYLOAD_TOO_LARGE, "图片文件超过允许大小");
        }
        String objectKey = consumeUploadTicket(ticket);
        if (objectKey == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "上传票据不存在或已过期");
        }
        // 扩展名在签发票据时已经确定，上传时再次核对，避免换一个 Content-Type 写入错误后缀。
        if (!objectKey.endsWith("." + extension)) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "图片类型与上传票据不一致");
        }
        Path target = resolveObjectKey(objectKey);
        Path temporary = null;
        try {
            Path temporaryDirectory = storageRoot().resolve(".tmp");
            Files.createDirectories(temporaryDirectory);
            temporary = Files.createTempFile(temporaryDirectory, "image-", ".part");
            long actualSize = copyWithLimit(inputStream, temporary);
            validateMagicBytes(temporary, normalizedType);
            if (actualSize <= 0 || actualSize > MAX_IMAGE_BYTES) {
                throw new BusinessException(ResultCode.PAYLOAD_TOO_LARGE, "图片文件超过允许大小");
            }
            Files.createDirectories(target.getParent());
            moveAtomically(temporary, target);
            temporary = null;
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            log.warn("local media upload failed, ticket={}, reason={}",
                    safeTicket(ticket), exception.getClass().getSimpleName());
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "图片存储暂不可用", exception);
        } finally {
            deleteTemporary(temporary);
        }
    }

    @Override
    public String validateUploaded(long sellerId, long listingId, ListingImageDTO request) {
        // 图片元数据必须绑定当前卖家/商品和规范文件名，防止通过 ../ 或路径分隔符引用其他商品的文件。
        Path imagePath = resolveListingImagePath(sellerId, listingId, request.objectKey());
        String contentType = normalizeContentType(request.contentType());
        String extension = EXTENSIONS.get(contentType);
        if (extension == null || !request.objectKey().endsWith("." + extension)) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "图片类型不合法");
        }
        try {
            if (!Files.isRegularFile(imagePath)) {
                throw new BusinessException(ResultCode.INVALID_ARGUMENT, "图片尚未上传或不可读取");
            }
            long actualSize = Files.size(imagePath);
            if (request.sizeBytes() != actualSize || actualSize > MAX_IMAGE_BYTES) {
                // 客户端元数据和文件实际大小必须一致，否则审核页展示的图片可能不是用户确认的文件。
                throw new BusinessException(ResultCode.INVALID_ARGUMENT, "图片实际内容与元数据不一致");
            }
            validateMagicBytes(imagePath, contentType);
            return sha256(imagePath);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "图片尚未上传或不可读取", exception);
        }
    }

    @Override
    public String validateAvatar(long userId, AvatarRegistrationDTO request) {
        Path avatarPath = resolveAvatarPath(userId, request.objectKey());
        String contentType = normalizeContentType(request.contentType());
        String extension = EXTENSIONS.get(contentType);
        if (extension == null || !request.objectKey().endsWith("." + extension)) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "头像类型不合法");
        }
        try {
            if (!Files.isRegularFile(avatarPath)) {
                throw new BusinessException(ResultCode.INVALID_ARGUMENT, "头像尚未上传或不可读取");
            }
            long actualSize = Files.size(avatarPath);
            if (request.sizeBytes() != actualSize || actualSize > MAX_IMAGE_BYTES) {
                throw new BusinessException(ResultCode.INVALID_ARGUMENT, "头像实际内容与元数据不一致");
            }
            validateMagicBytes(avatarPath, contentType);
            return request.objectKey();
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "头像尚未上传或不可读取", exception);
        }
    }

    @Override
    public MediaContent openAvatar(long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null || user.getAvatarObjectKey() == null || user.getAvatarObjectKey().isBlank()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "头像不存在");
        }
        Path avatarPath = resolveAvatarPath(userId, user.getAvatarObjectKey());
        String extension = user.getAvatarObjectKey().substring(user.getAvatarObjectKey().lastIndexOf('.') + 1);
        String contentType = EXTENSIONS.entrySet().stream()
                .filter(entry -> entry.getValue().equals(extension))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "头像格式不可用"));
        try {
            long size = Files.size(avatarPath);
            if (!Files.isRegularFile(avatarPath) || size > MAX_IMAGE_BYTES) {
                throw new BusinessException(ResultCode.NOT_FOUND, "头像不存在");
            }
            validateMagicBytes(avatarPath, contentType);
            return new MediaContent(avatarPath, contentType, size);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.NOT_FOUND, "头像不存在", exception);
        }
    }

    @Override
    public ImageUrlVO readUrl(Long viewerId, long listingId, long imageId) {
        ListingDO listing = listingMapper.selectById(listingId);
        ListingImageDO image = listing == null ? null : imageCodec.find(listing, imageId);
        if (listing == null || image == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "图片不存在");
        }
        if (!PUBLIC_LISTING_STATUSES.contains(listing.getStatus())
                && !listing.getSellerId().equals(viewerId)) {
            // 草稿和待审核图片只允许卖家读取；公开渠道图片才允许其他用户按短时地址查看。
            throw new BusinessException(ResultCode.FORBIDDEN, "无权读取该图片");
        }
        return issueImageUrl(listingId, image);
    }

    @Override
    public ImageUrlVO readUrlForReview(long listingId, long imageId) {
        ListingDO listing = listingMapper.selectById(listingId);
        ListingImageDO image = listing == null ? null : imageCodec.find(listing, imageId);
        if (listing == null || image == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "图片不存在");
        }
        // 人工审核必须核对实拍图：这里跳过卖家归属判断，调用入口只有后台端点，
        // 权限由 AdminController 的类级 @PreAuthorize 收敛。
        return issueImageUrl(listingId, image);
    }

    @Override
    public String coverUrl(ListingDO listing) {
        if (listing == null || !PUBLIC_LISTING_STATUSES.contains(listing.getStatus())) {
            return null;
        }
        ListingImageDO first = imageCodec.read(listing).stream().findFirst().orElse(null);
        if (first == null) {
            return null;
        }
        try {
            return issueImageUrl(listing.getId(), first).url();
        } catch (Exception exception) {
            // 封面只是展示增强：媒体异常不能阻断商品列表和详情的读取。
            return null;
        }
    }

    private ImageUrlVO issueImageUrl(long listingId, ListingImageDO image) {
        verifyStoredImage(image);
        String ticket = issueReadTicket(listingId, image.getId());
        return new ImageUrlVO(publicUrl("/api/v1/media/images/" + ticket),
                Instant.now().plus(properties.readTicketTtl()));
    }

    @Override
    public MediaContent openContent(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "图片票据不存在或已过期");
        }
        String value = readRedisValue(READ_KEY_PREFIX + ticket);
        if (value == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "图片票据不存在或已过期");
        }
        long listingId;
        long imageId;
        try {
            String[] parts = value.split(":", 2);
            listingId = Long.parseLong(parts[0]);
            imageId = Long.parseLong(parts[1]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "图片票据数据损坏", exception);
        }
        ListingDO listing = listingMapper.selectById(listingId);
        ListingImageDO image = listing == null ? null : imageCodec.find(listing, imageId);
        if (image == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "图片不存在");
        }
        Path imagePath = verifyStoredImage(image);
        return new MediaContent(imagePath, image.getContentType(), image.getSizeBytes());
    }

    @Override
    public Media mediaFor(ListingImageDO image) {
        verifyStoredImage(image);
        String ticket = issueReadTicket(image.getListingId(), image.getId());
        String relativePath = "/api/v1/media/images/" + ticket;
        return new Media(MimeTypeUtils.parseMimeType(image.getContentType()),
                properties.publicBaseUrl().resolve(relativePath));
    }

    @Override
    public boolean isPublicEndpointReachableByModel() {
        String host = properties.publicBaseUrl().getHost();
        // 本机地址对远程模型不可见，此时不发送图片，避免审核请求无意义超时。
        return host != null && !Set.of("localhost", "127.0.0.1", "::1")
                .contains(host.toLowerCase(Locale.ROOT));
    }

    private ListingDO requireEditableListing(long sellerId, long listingId) {
        ListingDO listing = listingMapper.selectById(listingId);
        if (listing == null || !listing.getSellerId().equals(sellerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权上传该商品图片");
        }
        if (listing.getStatus() != ListingStatus.DRAFT
                && listing.getStatus() != ListingStatus.CHANGES_REQUIRED) {
            throw new BusinessException(ResultCode.CONFLICT, "当前状态不能上传图片");
        }
        return listing;
    }

    private String issueUploadTicket(String objectKey) {
        String ticket = UUID.randomUUID().toString();
        writeRedisValue(UPLOAD_KEY_PREFIX + ticket, objectKey, properties.uploadTicketTtl());
        return ticket;
    }

    private String consumeUploadTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        try {
            // GETDEL 保证同一上传票据最多只能成功进入一次写入流程。
            return redisTemplate.opsForValue().getAndDelete(UPLOAD_KEY_PREFIX + ticket);
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "图片票据服务暂不可用", exception);
        }
    }

    private String issueReadTicket(long listingId, long imageId) {
        String ticket = UUID.randomUUID().toString();
        writeRedisValue(READ_KEY_PREFIX + ticket, listingId + ":" + imageId,
                properties.readTicketTtl());
        return ticket;
    }

    private void writeRedisValue(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "图片票据服务暂不可用", exception);
        }
    }

    private String readRedisValue(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "图片票据服务暂不可用", exception);
        }
    }

    private long copyWithLimit(InputStream inputStream, Path target) throws IOException {
        byte[] buffer = new byte[BUFFER_BYTES];
        long total = 0L;
        try (OutputStream outputStream = Files.newOutputStream(target,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    throw new BusinessException(ResultCode.PAYLOAD_TOO_LARGE, "图片文件超过允许大小");
                }
                outputStream.write(buffer, 0, read);
            }
        }
        return total;
    }

    private void validateMagicBytes(Path imagePath, String contentType) throws IOException {
        byte[] header;
        try (InputStream inputStream = Files.newInputStream(imagePath)) {
            // Content-Type 可以由客户端伪造，因此再检查文件头来确认真实图片格式。
            header = inputStream.readNBytes(12);
        }
        boolean valid = switch (contentType) {
            case "image/png" -> header.length >= 8
                    && header[0] == (byte) 0x89 && header[1] == 0x50
                    && header[2] == 0x4E && header[3] == 0x47;
            case "image/jpeg" -> header.length >= 3
                    && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8
                    && header[2] == (byte) 0xFF;
            case "image/webp" -> header.length >= 12
                    && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
        if (!valid) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "图片文件内容不合法");
        }
    }

    private Path verifyStoredImage(ListingImageDO image) {
        Path imagePath = resolveObjectKey(image.getObjectKey());
        try {
            if (!Files.isRegularFile(imagePath)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "图片文件不存在");
            }
            long actualSize = Files.size(imagePath);
            if (actualSize != image.getSizeBytes()) {
                throw new BusinessException(ResultCode.CONFLICT, "图片文件大小在确认后发生变化");
            }
            String hash = sha256(imagePath);
            if (image.getEtag() == null) {
                throw new BusinessException(ResultCode.CONFLICT, "图片尚未完成哈希确认");
            } else if (!image.getEtag().equals(hash)) {
                // 数据库哈希与本地文件不一致时停止返回，避免展示确认后被替换的图片。
                throw new BusinessException(ResultCode.CONFLICT, "图片文件在确认后发生变化");
            }
            return imagePath;
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "图片读取失败", exception);
        }
    }

    private Path resolveObjectKey(String objectKey) {
        Path root = storageRoot();
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "图片路径不合法");
        }
        return resolved;
    }

    private Path resolveListingImagePath(long sellerId, long listingId, String objectKey) {
        String prefix = listingObjectPrefix(sellerId, listingId);
        if (objectKey == null || !objectKey.startsWith(prefix)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "图片对象键不属于当前商品");
        }
        String fileName = objectKey.substring(prefix.length());
        if (!IMAGE_FILE_NAME.matcher(fileName).matches()) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "图片对象键格式不合法");
        }
        return resolveObjectKey(prefix + fileName);
    }

    private String listingObjectPrefix(long sellerId, long listingId) {
        return "users/" + sellerId + "/listings/" + listingId + "/";
    }

    private Path resolveAvatarPath(long userId, String objectKey) {
        String prefix = avatarObjectPrefix(userId);
        if (objectKey == null || !objectKey.startsWith(prefix)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "头像对象键不属于当前用户");
        }
        String fileName = objectKey.substring(prefix.length());
        if (!IMAGE_FILE_NAME.matcher(fileName).matches()) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "头像对象键格式不合法");
        }
        return resolveObjectKey(prefix + fileName);
    }

    private String avatarObjectPrefix(long userId) {
        return "users/" + userId + "/avatar/";
    }

    private String publicUrl(String relativePath) {
        // 默认使用同源相对地址，让 Vite 和 Nginx 可以统一代理；独立媒体域名由部署配置显式覆盖。
        return properties.publicBaseUrl().resolve(relativePath).toString();
    }

    private Path storageRoot() {
        return Path.of(properties.root()).toAbsolutePath().normalize();
    }

    private String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException exception) {
            log.warn("temporary media cleanup failed, file={}, reason={}",
                    temporary.getFileName(), exception.getClass().getSimpleName());
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameterIndex = contentType.indexOf(';');
        String value = parameterIndex >= 0 ? contentType.substring(0, parameterIndex) : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String safeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return "-";
        }
        return ticket.length() <= 8 ? ticket : ticket.substring(0, 8);
    }
}
