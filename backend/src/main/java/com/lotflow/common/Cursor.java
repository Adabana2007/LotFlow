package com.lotflow.common;

/** 游标分页参数解析。 */
public final class Cursor {

    private Cursor() {
    }

    /** 空游标表示第一页，Mapper 用动态条件省略；非法游标是客户端错误，不能退化成 500。 */
    public static Long parse(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "游标格式不合法");
        }
    }
}
