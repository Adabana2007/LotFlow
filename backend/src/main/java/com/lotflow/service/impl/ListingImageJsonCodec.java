package com.lotflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotflow.entity.ListingDO;
import com.lotflow.entity.ListingImageDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 在商品 JSON 快照中读写有序图片元数据。 */
@Component
@RequiredArgsConstructor
public class ListingImageJsonCodec {

    private static final TypeReference<List<ListingImageDO>> IMAGE_LIST =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;

    public List<ListingImageDO> read(ListingDO listing) {
        if (listing == null || listing.getImagesJson() == null || listing.getImagesJson().isBlank()) {
            return List.of();
        }
        try {
            List<ListingImageDO> images = objectMapper.readValue(listing.getImagesJson(), IMAGE_LIST);
            return images.stream()
                    .sorted(Comparator.comparing(ListingImageDO::getSortOrder))
                    .toList();
        } catch (JsonProcessingException exception) {
            // 图片快照只影响展示和审核，不在读取路径猜测修复损坏 JSON。
            throw new IllegalStateException("商品图片元数据损坏", exception);
        }
    }

    public ListingImageDO find(ListingDO listing, long imageId) {
        return read(listing).stream()
                .filter(image -> image.getId() != null && image.getId() == imageId)
                .findFirst()
                .orElse(null);
    }

    public void append(ListingDO listing, ListingImageDO image) {
        List<ListingImageDO> images = new ArrayList<>(read(listing));
        image.setListingId(listing.getId());
        image.setSortOrder(images.size());
        images.add(image);
        listing.setImagesJson(write(images));
    }

    private String write(List<ListingImageDO> images) {
        try {
            return objectMapper.writeValueAsString(images);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化商品图片元数据", exception);
        }
    }
}
