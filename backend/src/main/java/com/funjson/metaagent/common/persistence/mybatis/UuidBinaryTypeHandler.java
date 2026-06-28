package com.funjson.metaagent.common.persistence.mybatis;

import java.nio.ByteBuffer;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * 在 Java {@link UUID} 与 MySQL {@code BINARY(16)} 主键之间进行无损转换。
 *
 * <p>领域层始终使用 UUID，数据库层保持紧凑的二进制存储，避免业务代码反复调用
 * {@code UUID_TO_BIN}/{@code BIN_TO_UUID}。</p>
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(JdbcType.BINARY)
public class UuidBinaryTypeHandler extends BaseTypeHandler<UUID> {

    /**
     * 写入非空 UUID 参数。
     *
     * @param statement JDBC 预编译语句
     * @param parameter UUID 参数
     * @param index 参数位置
     * @param jdbcType JDBC 类型
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void setNonNullParameter(
            PreparedStatement statement,
            int index,
            UUID parameter,
            JdbcType jdbcType) throws SQLException {
        statement.setBytes(index, toBytes(parameter));
    }

    /**
     * 按列名读取 UUID。
     *
     * @param resultSet 查询结果
     * @param columnName 列名
     * @return UUID，数据库值为空时返回 {@code null}
     * @throws SQLException 读取失败时抛出
     */
    @Override
    public UUID getNullableResult(
            ResultSet resultSet,
            String columnName) throws SQLException {
        return fromBytes(resultSet.getBytes(columnName));
    }

    /**
     * 按列序号读取 UUID。
     *
     * @param resultSet 查询结果
     * @param columnIndex 列序号
     * @return UUID，数据库值为空时返回 {@code null}
     * @throws SQLException 读取失败时抛出
     */
    @Override
    public UUID getNullableResult(
            ResultSet resultSet,
            int columnIndex) throws SQLException {
        return fromBytes(resultSet.getBytes(columnIndex));
    }

    /**
     * 从存储过程结果读取 UUID。
     *
     * @param statement 存储过程语句
     * @param columnIndex 列序号
     * @return UUID，数据库值为空时返回 {@code null}
     * @throws SQLException 读取失败时抛出
     */
    @Override
    public UUID getNullableResult(
            CallableStatement statement,
            int columnIndex) throws SQLException {
        return fromBytes(statement.getBytes(columnIndex));
    }

    /**
     * 将 UUID 转换为 16 字节二进制值。
     *
     * @param value UUID
     * @return 二进制表示
     */
    private byte[] toBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    /**
     * 将 16 字节二进制值转换为 UUID。
     *
     * @param bytes 数据库二进制值
     * @return UUID；输入为空时返回 {@code null}
     */
    private UUID fromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
