package com.lotflow.ai.search;

import com.lotflow.mapper.ListingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 向量索引失败补偿任务。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorIndexTask {

    private final ListingMapper listingMapper;
    private final VectorIndexService indexService;

    @Scheduled(fixedDelayString = "${lotflow.ai.vector-retry-delay:60000}")
    public void retryFailedIndexes() {
        // 索引状态归属商品行，失败重试不依赖某一条 MQ 消息是否仍在队列。
        listingMapper.selectRetryableVectors().forEach(listing -> {
            try {
                indexService.index(listing.getId());
            } catch (RuntimeException exception) {
                log.warn("vector index retry failed, listingId={}", listing.getId());
            }
        });
    }
}
