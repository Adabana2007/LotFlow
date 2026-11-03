package com.lotflow.common;

import java.util.List;

/** 游标分页响应，避免大偏移量的深分页。 */
public record CursorPageVO<T>(List<T> items, String nextCursor) {
}
