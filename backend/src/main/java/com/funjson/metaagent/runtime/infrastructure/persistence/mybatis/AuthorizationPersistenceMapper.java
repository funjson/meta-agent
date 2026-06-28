package com.funjson.metaagent.runtime.infrastructure.persistence.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AuthorizationRequest MyBatis Mapper。
 */
@Mapper
public interface AuthorizationPersistenceMapper {

    /** @return 插入行数 */
    int insert(
            @Param("id") UUID id,
            @Param("jobId") UUID jobId,
            @Param("taskRunId") UUID taskRunId,
            @Param("loopNodeId") UUID loopNodeId,
            @Param("requestType") String requestType,
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("requestedDeltaJson") String requestedDeltaJson);

    /** @return 请求行 */
    List<Map<String, Object>> findByStatus(
            @Param("status") String status);

    /** @return 更新行数 */
    int decide(
            @Param("id") UUID id,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("decisionJson") String decisionJson);
}
