package com.lotflow.controller;

import com.lotflow.ai.search.SearchService;
import com.lotflow.common.CursorPageVO;
import com.lotflow.vo.ListingVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 商品混合搜索接口。 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/listings")
    public CursorPageVO<ListingVO> listings(@RequestParam String q,
                                            @RequestParam(required = false) String cursor,
                                            @RequestParam(defaultValue = "20") int limit) {
        // 搜索接口始终走 SearchService；Milvus 或重排故障时由 Service 保留 PostgreSQL 关键词结果。
        return searchService.search(q, cursor, limit);
    }
}
