package com.funjson.metaagent.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.capability.application.CapabilityApplicationService;
import com.funjson.metaagent.capability.application.CapabilityDerivationResolver;
import com.funjson.metaagent.capability.application.CapabilityLoadService;
import com.funjson.metaagent.capability.application.CapabilityRegistry;
import com.funjson.metaagent.capability.application.CapabilityScopeResolver;
import com.funjson.metaagent.capability.infrastructure.persistence.mybatis.CapabilityRepository;
import com.funjson.metaagent.loop.domain.LoopRunParentType;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.loop.infrastructure.persistence.mybatis.RuntimeRepository;
import org.junit.jupiter.api.Test;

/**
 * 验证规范型 Skill 只沿当前 Child Loop 子树继承。
 */
class CapabilityApplicationServiceTest {

    @Test
    void inheritsParentPolicyIntoChildNodeScope() {
        CapabilityRegistry registry = mock(CapabilityRegistry.class);
        CapabilityRepository repository = mock(CapabilityRepository.class);
        RuntimeRepository runtimeRepository = mock(RuntimeRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        CapabilityApplicationService service =
                new CapabilityApplicationService(
                        repository,
                        new CapabilityLoadService(
                                registry,
                                repository,
                                runtimeRepository,
                                objectMapper),
                        new CapabilityScopeResolver(objectMapper),
                        new CapabilityDerivationResolver(objectMapper),
                        objectMapper);
        UUID parentNodeId = UUID.randomUUID();
        RunExecutionContext child = context(parentNodeId);
        when(repository.findAppliedLoads(child.loopNodeId()))
                .thenReturn(List.of(Map.of(
                        "loadId", UUID.randomUUID().toString(),
                        "sourceId", "local-policy",
                        "sourceVersion", 1,
                        "capabilityType", "POLICY",
                        "descriptorJson",
                        """
                        {
                          "instructions": ["only this subtree"],
                          "policy": {"maxRetries": 2}
                        }
                        """)));

        var result = service.prepare(child);

        assertThat(result.scopedContext().instructions())
                .containsExactly("only this subtree");
        assertThat(result.scopedContext().policy())
                .containsEntry("maxRetries", 2);
        verify(repository).inheritPolicyLoads(
                parentNodeId,
                child.loopNodeId(),
                child.taskRunId(),
                child.loopRunId());
        verify(repository).updateScopedContext(
                eq(child.loopNodeId()),
                anyString());
    }

    /**
     * 创建 Child Loop 上下文。
     *
     * @param parentNodeId 父节点 ID
     * @return 上下文
     */
    private RunExecutionContext context(UUID parentNodeId) {
        UUID taskRunId = UUID.randomUUID();
        return new RunExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                taskRunId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                parentNodeId,
                1,
                2,
                LoopRunParentType.TASK_RUN,
                taskRunId,
                0,
                "fake",
                "goal",
                "",
                null);
    }
}
