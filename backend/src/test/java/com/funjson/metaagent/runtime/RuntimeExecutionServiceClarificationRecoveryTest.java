package com.funjson.metaagent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.funjson.metaagent.capability.application.CapabilityApplicationService;
import com.funjson.metaagent.capability.domain.CapabilityPlanningContext;
import com.funjson.metaagent.context.application.LoopContextBuilder;
import com.funjson.metaagent.context.domain.LoopContextSnapshot;
import com.funjson.metaagent.loop.application.RuntimeExecutionService;
import com.funjson.metaagent.loop.application.RuntimeTransactionService;
import com.funjson.metaagent.loop.domain.ClarificationNeedDetector;
import com.funjson.metaagent.loop.domain.ExecutionDerivationPolicy;
import com.funjson.metaagent.loop.domain.LoopActionResult;
import com.funjson.metaagent.loop.domain.LoopActionType;
import com.funjson.metaagent.loop.domain.LoopEvaluation;
import com.funjson.metaagent.loop.domain.LoopEvaluationDecision;
import com.funjson.metaagent.loop.domain.LoopCompletionPolicy;
import com.funjson.metaagent.loop.domain.LoopExecutionPolicy;
import com.funjson.metaagent.loop.domain.LoopOutcome;
import com.funjson.metaagent.loop.domain.LoopPhaseType;
import com.funjson.metaagent.loop.domain.LoopPlanner;
import com.funjson.metaagent.loop.domain.LoopRunParentType;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.prompt.domain.RenderedPrompt;
import com.funjson.metaagent.prompt.application.PromptRegistry;
import com.funjson.metaagent.provider.application.ModelProviderRegistry;
import com.funjson.metaagent.provider.domain.ModelProvider;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.provider.domain.ModelResponse;
import com.funjson.metaagent.recovery.application.RuntimeLeaseService;
import com.funjson.metaagent.recovery.domain.LoopNodeResumeContext;
import com.funjson.metaagent.runtime.domain.ClarificationAnswerOutcome;
import com.funjson.metaagent.tool.application.ToolExecutionService;
import com.funjson.metaagent.tool.application.ToolInvocationCommand;
import org.junit.jupiter.api.Test;

/**
 * 验证 LoopNode 在人工澄清恢复后不会重复写动作执行阶段。
 */
class RuntimeExecutionServiceClarificationRecoveryTest {

    @Test
    void modelClarificationIsUpgradedWithRuntimeContract() {
        RuntimeTransactionService transactions =
                mock(RuntimeTransactionService.class);
        ModelProviderRegistry modelProviders =
                mock(ModelProviderRegistry.class);
        PromptRegistry promptRegistry = mock(PromptRegistry.class);
        CapabilityApplicationService capabilities =
                mock(CapabilityApplicationService.class);
        LoopContextBuilder contextBuilder = mock(LoopContextBuilder.class);
        ToolExecutionService tools = mock(ToolExecutionService.class);
        RuntimeExecutionService service = new RuntimeExecutionService(
                transactions,
                modelProviders,
                promptRegistry,
                new LoopPlanner(),
                mock(LoopCompletionPolicy.class),
                new ClarificationNeedDetector(),
                mock(ExecutionDerivationPolicy.class),
                capabilities,
                mock(RuntimeLeaseService.class),
                contextBuilder,
                tools);
        RunExecutionContext context = context();
        UUID requestId = UUID.randomUUID();
        RenderedPrompt prompt = new RenderedPrompt(
                "loop.execution",
                "v2",
                "system",
                "user",
                "hash");
        ModelProvider provider = mock(ModelProvider.class);
        when(capabilities.prepare(context))
                .thenReturn(CapabilityPlanningContext.empty());
        when(contextBuilder.build(eq(context), any()))
                .thenReturn(new LoopContextSnapshot(
                        context.taskRunId(),
                        context.loopNodeId(),
                        List.of(),
                        1024));
        when(promptRegistry.render(any(), any()))
                .thenReturn(prompt);
        when(transactions.startExternalAction(context, prompt))
                .thenReturn(new RuntimeTransactionService.ExternalActionHandle(
                        UUID.randomUUID()));
        when(modelProviders.require("fake")).thenReturn(provider);
        when(provider.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(
                        "fake",
                        "fake-model",
                        "请补充姓名、学历、工作经验、岗位和风格。",
                        "stop"));
        when(tools.invokeForLoop(eq(context), any()))
                .thenReturn(new LoopActionResult(
                        LoopActionType.TOOL_CALL,
                        "tool:" + requestId,
                        "请补充姓名、学历、工作经验、岗位和风格。",
                        Map.of(
                                "clarificationRequestId", requestId,
                                "question", "请补充姓名、学历、工作经验、岗位和风格。")));
        when(transactions.suspendForClarification(
                eq(context),
                eq(requestId),
                anyString())).thenReturn(LoopOutcome.waitingHuman(context));

        LoopOutcome outcome = service.execute(context);

        var commandCaptor = forClass(ToolInvocationCommand.class);
        verify(tools).invokeForLoop(eq(context), commandCaptor.capture());
        ToolInvocationCommand command = commandCaptor.getValue();
        assertThat(outcome.status()).isEqualTo(
                LoopOutcome.OutcomeStatus.WAITING_HUMAN);
        assertThat(command.toolId()).isEqualTo("clarification.request");
        assertThat(command.arguments().get("contractJson").toString())
                .contains("\"version\": \"runtime-v1\"")
                .contains("\"key\": \"role\"")
                .contains("\"defaultable\": true");
    }

    @Test
    void clarificationResumeContinuesFromObservationWithoutNewActionPhase() {
        RuntimeTransactionService transactions =
                mock(RuntimeTransactionService.class);
        LoopCompletionPolicy completionPolicy =
                mock(LoopCompletionPolicy.class);
        RuntimeExecutionService service = service(
                transactions,
                completionPolicy);
        RunExecutionContext context = context();
        LoopEvaluation evaluation = new LoopEvaluation(
                LoopEvaluationDecision.COMPLETE,
                "澄清回答满足局部目标",
                "");
        LoopOutcome expected = LoopOutcome.completed(
                context,
                "已根据澄清继续完成",
                UUID.randomUUID());
        when(transactions.nodeCount(context.loopRunId())).thenReturn(1);
        when(completionPolicy.evaluate(
                eq(context),
                any(LoopActionResult.class),
                any(LoopExecutionPolicy.class),
                eq(1))).thenReturn(evaluation);
        when(transactions.complete(
                eq(context),
                any(LoopActionResult.class),
                eq(evaluation))).thenReturn(expected);

        LoopOutcome actual = service.completeRecoveredClarificationAction(
                new LoopNodeResumeContext(
                        context,
                        "澄清回答可继续执行"),
                new ClarificationAnswerOutcome(
                        UUID.randomUUID(),
                        "请补充用途和背景",
                        "用于求职面试"));

        assertThat(actual).isSameAs(expected);
        verify(transactions, never()).recordCompletedPhase(
                eq(context),
                eq(LoopPhaseType.ACTION_EXECUTION),
                anyString(),
                any(),
                any(),
                anyString());
        verify(transactions).recordCompletedPhase(
                eq(context),
                eq(LoopPhaseType.OBSERVATION),
                anyString(),
                any(),
                any(),
                eq("OBSERVATION_RECORDED"));
        verify(transactions).recordCompletedPhase(
                eq(context),
                eq(LoopPhaseType.EVALUATION),
                eq(evaluation.summary()),
                any(),
                any(),
                eq("EVALUATION_RECORDED"));
    }

    /**
     * 创建只覆盖澄清恢复路径的 RuntimeExecutionService。
     *
     * @param transactions Loop 事务服务
     * @param completionPolicy Loop 局部验收策略
     * @return RuntimeExecutionService
     */
    private RuntimeExecutionService service(
            RuntimeTransactionService transactions,
            LoopCompletionPolicy completionPolicy) {
        return new RuntimeExecutionService(
                transactions,
                mock(ModelProviderRegistry.class),
                mock(PromptRegistry.class),
                mock(LoopPlanner.class),
                completionPolicy,
                mock(ClarificationNeedDetector.class),
                mock(ExecutionDerivationPolicy.class),
                mock(CapabilityApplicationService.class),
                mock(RuntimeLeaseService.class),
                mock(LoopContextBuilder.class),
                mock(ToolExecutionService.class));
    }

    /** @return 测试用 LoopNode 上下文 */
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
                "生成个人简历",
                "",
                null);
    }
}
