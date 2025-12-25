package com.jipjung.project.global.mybatis;

import com.jipjung.project.domain.DreamHomeStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Fixes DreamHomeStatus <-> VARCHAR mapping to avoid enum binding issues.
 */
@MappedTypes(DreamHomeStatus.class)
public class DreamHomeStatusTypeHandler extends BaseTypeHandler<DreamHomeStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, DreamHomeStatus parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public DreamHomeStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value != null ? DreamHomeStatus.valueOf(value) : null;
    }

    @Override
    public DreamHomeStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value != null ? DreamHomeStatus.valueOf(value) : null;
    }

    @Override
    public DreamHomeStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value != null ? DreamHomeStatus.valueOf(value) : null;
    }
}
