package com.funjson.metaagent.runtime.application;

import com.funjson.metaagent.runtime.api.AuthorizationRequestView;
import com.funjson.metaagent.runtime.application.port.out.AuthorizationStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 创建、查询和决定可委托权限或预算请求。
 */
@Service
public class AuthorizationRequestService {

    private final AuthorizationStore store;

    /**
     * 创建授权服务。
     *
     * @param store 授权持久化端口
     */
    public AuthorizationRequestService(AuthorizationStore store) {
        this.store = store;
    }

    /**
     * 创建待授权请求。
     *
     * @return 请求 ID
     */
    @Transactional
    public UUID create(
            UUID jobId,
            UUID taskRunId,
            UUID loopNodeId,
            String requestType,
            String sourceType,
            String sourceId,
            Map<String, Object> requestedDelta) {
        UUID id = UUID.randomUUID();
        store.insert(
                id,
                jobId,
                taskRunId,
                loopNodeId,
                requestType,
                sourceType,
                sourceId,
                requestedDelta);
        return id;
    }

    /** @return 指定状态请求 */
    @Transactional(readOnly = true)
    public List<AuthorizationRequestView> list(String status) {
        return store.findByStatus(status);
    }

    /** 批准请求。 */
    @Transactional
    public void approve(UUID id, String summary) {
        store.decide(
                id,
                "PENDING",
                "APPROVED",
                Map.of("summary", summary));
    }

    /** 拒绝请求。 */
    @Transactional
    public void reject(UUID id, String summary) {
        store.decide(
                id,
                "PENDING",
                "REJECTED",
                Map.of("summary", summary));
    }
}
