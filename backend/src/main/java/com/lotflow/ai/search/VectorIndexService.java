package com.lotflow.ai.search;

import com.lotflow.entity.ListingDO;
import com.lotflow.entity.enums.DomainEnums.VectorIndexStatus;
import com.lotflow.mapper.ListingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** 商品向量索引和失败补偿状态。 */
@Service
@RequiredArgsConstructor
public class VectorIndexService {

    private final ListingMapper listingMapper;
    @Qualifier("listingVectorStore")
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    public void index(long listingId) {
        ListingDO listing = listingMapper.selectById(listingId);
        if (listing == null) {
            return;
        }
        VectorStore store = vectorStoreProvider.getIfAvailable();
        if (store == null) {
            markFailed(listing, "vector store unavailable");
            return;
        }
        try {
            Document document = new Document(listingId + ":index",
                    listing.getTitle() + "\n" + listing.getDescription() + "\n"
                            + listing.getCategoryCode() + "\n" + listing.getConditionCode(),
                    Map.of("listingId", listingId, "status", listing.getStatus().name()));
            // 只有向量写入成功才标记 INDEXED；数据库状态用于定时补偿和 MQ 重试后的收敛。
            store.add(List.of(document));
            listing.setVectorStatus(VectorIndexStatus.INDEXED);
            listing.setVectorIndexedAt(OffsetDateTime.now(ZoneOffset.UTC));
            listing.setVectorLastError(null);
            listingMapper.updateById(listing);
        } catch (RuntimeException exception) {
            // 向量库故障不反向影响审核或交易，只累加可重试状态。
            markFailed(listing, exception.getClass().getSimpleName());
        }
    }

    private void markFailed(ListingDO listing, String error) {
        listing.setVectorStatus(VectorIndexStatus.FAILED);
        listing.setVectorRetryCount(listing.getVectorRetryCount() == null
                ? 1 : listing.getVectorRetryCount() + 1);
        listing.setVectorLastError(error);
        listingMapper.updateById(listing);
    }
}
