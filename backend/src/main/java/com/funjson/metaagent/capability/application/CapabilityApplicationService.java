package com.funjson.metaagent.capability.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.capability.application.port.out.CapabilityStore;
import com.funjson.metaagent.capability.domain.CapabilityPlanningContext;
import com.funjson.metaagent.capability.domain.CapabilityRef;
import com.funjson.metaagent.capability.domain.ScopedCapabilityContext;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 协调 Capability 加载、作用域解析和派生请求解析。
 *
 * <p>该类只保留 Loop Kernel 调用所需的门面语义；具体职责分别下沉到
 * CapabilityLoadService、CapabilityScopeResolver 和
 * CapabilityDerivationResolver。</p>
 */
@Service
public class CapabilityApplicationService {

    private final CapabilityStore repository;
    private final CapabilityLoadService loadService;
    private final CapabilityScopeResolver scopeResolver;
    private final CapabilityDerivationResolver derivationResolver;
    private final ObjectMapper objectMapper;

    /**
     * 创建 Capability Application Service。
     *
     * @param repository Capability Repository
     * @param loadService 加载服务
     * @param scopeResolver 作用域解析器
     * @param derivationResolver 派生解析器
     * @param objectMapper JSON Mapper
     */
    public CapabilityApplicationService(
            CapabilityStore repository,
            CapabilityLoadService loadService,
            CapabilityScopeResolver scopeResolver,
            CapabilityDerivationResolver derivationResolver,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.loadService = loadService;
        this.scopeResolver = scopeResolver;
        this.derivationResolver = derivationResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * 准备当前 LoopNode 的 Capability 作用域和派生动作。
     *
     * @param context LoopNode 上下文
     * @return Capability 规划上下文
     */
    @Transactional
    public CapabilityPlanningContext prepare(RunExecutionContext context) {
        loadService.inheritParentPolicies(context);
        if (context.pendingCapability() != null) {
            loadService.apply(
                    context,
                    context.pendingCapability());
        }

        var loads = repository.findAppliedLoads(context.loopNodeId());
        var scopedContext = scopeResolver.resolve(loads);
        repository.updateScopedContext(
                context.loopNodeId(),
                json(scopedContext));
        return new CapabilityPlanningContext(
                scopedContext,
                derivationResolver.resolve(context, loads));
    }

    /**
     * 应用显式配置或 Tool 选择的 Capability。
     *
     * @param context LoopNode 上下文
     * @param ref Capability 引用
     */
    @Transactional
    public void apply(
            RunExecutionContext context,
            CapabilityRef ref) {
        loadService.apply(context, ref);
    }

    /** 序列化 Capability 作用域快照。 */
    private String json(ScopedCapabilityContext value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize capability scope",
                    exception);
        }
    }
}
