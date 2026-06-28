package com.funjson.metaagent.capability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.funjson.metaagent.capability.domain.CapabilityPlanningContext;
import com.funjson.metaagent.capability.domain.ScopedCapabilityContext;
import com.funjson.metaagent.loop.domain.ExecutionDerivationRequest;
import com.funjson.metaagent.loop.domain.LoopActionType;
import com.funjson.metaagent.loop.domain.LoopPlanner;
import com.funjson.metaagent.loop.domain.LoopRunParentType;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.runtime.domain.ChildJobRequest;
import com.funjson.metaagent.runtime.domain.ContractContribution;
import com.funjson.metaagent.runtime.domain.CapabilityRequest;
import com.funjson.metaagent.runtime.domain.TaskGraphTemplateRef;
import org.junit.jupiter.api.Test;

/**
 * 验证 Capability 通过结构化合同驱动 Loop Planner。
 */
class CapabilityLoopPlannerTest {

    private final LoopPlanner planner = new LoopPlanner();

    @Test
    void policyCapabilityKeepsModelAction() {
        var capabilityContext = new CapabilityPlanningContext(
                new ScopedCapabilityContext(
                        List.of("local instruction"),
                        Map.of("requireEvidence", true),
                        List.of()),
                null);

        assertThat(planner.plan(context(), capabilityContext).actionType())
                .isEqualTo(LoopActionType.MODEL_CALL);
    }

    @Test
    void stepCapabilityCreatesChildLoopAction() {
        var derivation = ExecutionDerivationRequest.childLoop(
                "step-load",
                "step capability",
                "child goal",
                "");

        assertThat(planner.plan(
                context(),
                new CapabilityPlanningContext(
                        ScopedCapabilityContext.empty(),
                        derivation)).actionType())
                .isEqualTo(LoopActionType.CHILD_LOOP);
    }

    @Test
    void childJobCapabilityCreatesChildJobAction() {
        var derivation = ExecutionDerivationRequest.childJob(
                "child job capability",
                new ChildJobRequest(
                        "child goal",
                        List.of("keep evidence"),
                        new TaskGraphTemplateRef("child-template", 1),
                        null,
                        "",
                        ContractContribution.empty(),
                        CapabilityRequest.none(),
                        "child-job-load",
                        "process-skill",
                        1));

        assertThat(planner.plan(
                context(),
                new CapabilityPlanningContext(
                        ScopedCapabilityContext.empty(),
                        derivation)).actionType())
                .isEqualTo(LoopActionType.CHILD_JOB);
    }

    /**
     * 创建规划上下文。
     *
     * @return Loop 上下文
     */
    private RunExecutionContext context() {
        UUID taskRunId = UUID.randomUUID();
        return new RunExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                taskRunId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                0,
                1,
                LoopRunParentType.TASK_RUN,
                taskRunId,
                0,
                "fake",
                "goal",
                "",
                null);
    }
}
