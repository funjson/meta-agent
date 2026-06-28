package com.funjson.metaagent.capability.application;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.capability.application.port.out.CapabilityStore;
import com.funjson.metaagent.capability.domain.CapabilityDescriptor;
import com.funjson.metaagent.capability.domain.CapabilityRef;
import com.funjson.metaagent.loop.application.port.out.RuntimeStore;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import org.springframework.stereotype.Service;

/**
 * 负责 Capability 的继承、显式加载和审计事件。
 */
@Service
public class CapabilityLoadService {

    private final CapabilityRegistry registry;
    private final CapabilityStore repository;
    private final RuntimeStore runtimeRepository;
    private final ObjectMapper objectMapper;

    /**
     * 创建 Capability Load Service。
     *
     * @param registry Capability Registry
     * @param repository Capability Store
     * @param runtimeRepository Runtime Store
     * @param objectMapper JSON Mapper
     */
    public CapabilityLoadService(
            CapabilityRegistry registry,
            CapabilityStore repository,
            RuntimeStore runtimeRepository,
            ObjectMapper objectMapper) {
        this.registry = registry;
        this.repository = repository;
        this.runtimeRepository = runtimeRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 从父 LoopNode 继承可传播的规范型 Capability。
     *
     * @param context 当前 LoopNode 上下文
     */
    public void inheritParentPolicies(RunExecutionContext context) {
        if (context.parentNodeId() == null) {
            return;
        }
        // 只有 POLICY + LOOP_NODE_SUBTREE 可以沿 Child Loop 继承。
        repository.inheritPolicyLoads(
                context.parentNodeId(),
                context.loopNodeId(),
                context.taskRunId(),
                context.loopRunId());
    }

    /**
     * 应用显式配置或 Tool 选择的 Capability。
     *
     * @param context LoopNode 上下文
     * @param ref Capability 引用
     */
    public void apply(
            RunExecutionContext context,
            CapabilityRef ref) {
        if (repository.findLoad(context.loopNodeId(), ref).isPresent()) {
            return;
        }
        CapabilityDescriptor descriptor = registry.resolve(ref);
        var source = repository.requireSource(ref);
        UUID loadId = UUID.randomUUID();
        repository.insertLoad(
                loadId,
                context.taskRunId(),
                context.loopRunId(),
                context.loopNodeId(),
                source);
        runtimeRepository.insertRuntimeEvent(
                UUID.randomUUID(),
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                "CAPABILITY_LOAD",
                loadId,
                "CAPABILITY_LOAD_COMPLETED",
                json(Map.of(
                        "capabilityLoadId", loadId,
                        "sourceId", ref.id(),
                        "sourceVersion", ref.version(),
                        "adapterId", descriptor.adapterId(),
                        "capabilityType", descriptor.type().name(),
                        "scopeType", descriptor.scopeType().name(),
                        "originLoopNodeId", context.loopNodeId())));
    }

    /** 序列化审计状态。 */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize capability state",
                    exception);
        }
    }
}
