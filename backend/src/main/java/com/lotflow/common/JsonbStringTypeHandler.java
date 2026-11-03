package com.lotflow.common;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/** 让已序列化的 JSON 字符串以 PostgreSQL jsonb 类型读写。 */
public class JsonbStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, String parameter,
                                    JdbcType jdbcType) throws SQLException {
        // 驱动按 PostgreSQL 未知类型写入，让数据库把参数解析为列定义的 jsonb。
        statement.setObject(index, parameter, Types.OTHER);
    }

    @Override
    public String getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement statement, int columnIndex)
            throws SQLException {
        return statement.getString(columnIndex);
    }
}
