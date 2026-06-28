package com.funjson.metaagent.runtime.application.port.out;

import com.funjson.metaagent.runtime.api.AuthorizationRequestView;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 授权请求持久化端口。
 */
public interface AuthorizationStore {

    /** 插入待授权请求。 */
    void insert(
            UUID id,
            UUID jobId,
            UUID taskRunId,
            UUID loopNodeId,
            String requestType,
            String sourceType,
            String sourceId,
            Map<String, Object> requestedDelta);

    /** @return 指定状态的授权请求 */
    List<AuthorizationRequestView> findByStatus(String status);

    /** 更新授权决定。 */
    void decide(
            UUID id,
            String expectedStatus,
            String targetStatus,
            Map<String, Object> decision);
}
