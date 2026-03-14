package com.wanqu.copilot.typehandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanqu.copilot.util.SpringContextHolder;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/** 将 Map<String,Object> 写入为 JSON 字符串（VARCHAR/TEXT），读取时将 JSON 解析成 Map。 */
@MappedTypes(Map.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class JsonMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

  private static ObjectMapper mapper;

  private static synchronized ObjectMapper getMapper() {
    if (mapper == null) {
      try {
        mapper = SpringContextHolder.getBean(ObjectMapper.class);
      } catch (Exception e) {
        // 回退到默认 mapper，如果 Spring 上下文尚未就绪
        mapper = new ObjectMapper();
      }
    }
    return mapper;
  }

  @Override
  public void setNonNullParameter(
      PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType)
      throws SQLException {
    try {
      String json = getMapper().writeValueAsString(parameter);
      ps.setString(i, json);
    } catch (Exception e) {
      throw new SQLException("Failed to convert Map to JSON string for MyBatis parameter.", e);
    }
  }

  @Override
  public Map<String, Object> getNullableResult(ResultSet rs, String columnName)
      throws SQLException {
    String json = rs.getString(columnName);
    if (json == null) {
      return null;
    }
    try {
      //noinspection unchecked
      return getMapper().readValue(json, Map.class);
    } catch (Exception e) {
      throw new SQLException("Failed to parse JSON string to Map for MyBatis result.", e);
    }
  }

  @Override
  public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    String json = rs.getString(columnIndex);
    if (json == null) {
      return null;
    }
    try {
      //noinspection unchecked
      return getMapper().readValue(json, Map.class);
    } catch (Exception e) {
      throw new SQLException("Failed to parse JSON string to Map for MyBatis result.", e);
    }
  }

  @Override
  public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex)
      throws SQLException {
    String json = cs.getString(columnIndex);
    if (json == null) {
      return null;
    }
    try {
      //noinspection unchecked
      return getMapper().readValue(json, Map.class);
    } catch (Exception e) {
      throw new SQLException("Failed to parse JSON string to Map for MyBatis callable result.", e);
    }
  }
}
