package com.funjson.metaagent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.funjson.metaagent.capability.application.CapabilityApplicationService;
import com.funjson.metaagent.capability.domain.CapabilityPlanningContext;
import com.funjson.metaagent.context.application.LoopContextBuilder;
import com.funjson.metaagent.context.domain.LoopContextSnapshot;
import com.funjson.metaagent.loop.application.ReActActionPlanner;
import com.funjson.metaagent.loop.application.RuntimeExecutionService;
import com.funjson.metaagent.loop.application.RuntimeTransactionService;
import com.funjson.metaagent.loop.domain.ClarificationNeedDetector;
import com.funjson.metaagent.loop.domain.ExecutionDerivationPolicy;
import com.funjson.metaagent.loop.domain.ExecutionDerivationRequest;
import com.funjson.metaagent.loop.domain.LoopActionResult;
import com.funjson.metaagent.loop.domain.LoopActionType;
import com.funjson.metaagent.loop.domain.LoopEvaluation;
import com.funjson.metaagent.loop.domain.LoopEvaluationDecision;
import com.funjson.metaagent.loop.domain.LoopCompletionPolicy;
import com.funjson.metaagent.loop.domain.LoopCorrectionPolicy;
import com.funjson.metaagent.loop.domain.LoopExecutionPolicy;
import com.funjson.metaagent.loop.domain.LoopOutcome;
import com.funjson.metaagent.loop.domain.LoopPhaseType;
import com.funjson.metaagent.loop.domain.LoopPlan;
import com.funjson.metaagent.loop.domain.LoopRunParentType;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.prompt.domain.RenderedPrompt;
import com.funjson.metaagent.prompt.application.PromptRegistry;
import com.funjson.metaagent.provider.application.ModelProviderRegistry;
import com.funjson.metaagent.provider.domain.ModelProvider;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.provider.domain.ModelResponse;
import com.funjson.metaagent.provider.domain.ModelToolCall;
import com.funjson.metaagent.provider.domain.ModelToolSpec;
import com.funjson.metaagent.recovery.application.RuntimeLeaseService;
import com.funjson.metaagent.recovery.domain.LoopNodeResumeContext;
import com.funjson.metaagent.runtime.application.port.out.TaskIntentScopeStore;
import com.funjson.metaagent.runtime.domain.ClarificationAnswerOutcome;
import com.funjson.metaagent.tool.application.ToolExecutionService;
import com.funjson.metaagent.tool.application.ToolCatalogService;
import com.funjson.metaagent.tool.application.ToolInvocationCommand;
import org.junit.jupiter.api.Test;

/**
 * 验证 LoopNode 在人工澄清恢复后不会重复写动作执行阶段。
 */
class RuntimeExecutionServiceClarificationRecoveryTest {

    @Test
    void nativeToolCallingBypassesJsonPlanner() {
        RuntimeTransactionService transactions =
                mock(RuntimeTransactionService.class);
        ModelProviderRegistry modelProviders =
                mock(ModelProviderRegistry.class);
        PromptRegistry promptRegistry = mock(PromptRegistry.class);
        ReActActionPlanner actionPlanner = mock(ReActActionPlanner.class);
        CapabilityApplicationService capabilities =
                mock(CapabilityApplicationService.class);
        LoopContextBuilder contextBuilder = mock(LoopContextBuilder.class);
        ToolExecutionService tools = mock(ToolExecutionService.class);
        ToolCatalogService toolCatalog = mock(ToolCatalogService.class);
        LoopCompletionPolicy completionPolicy =
                mock(LoopCompletionPolicy.class);
        RuntimeExecutionService service = new RuntimeExecutionService(
                transactions,
                modelProviders,
                promptRegistry,
                actionPlanner,
                completionPolicy,
                new LoopCorrectionPolicy(),
                new ClarificationNeedDetector(),
                mock(ExecutionDerivationPolicy.class),
                capabilities,
                mock(RuntimeLeaseService.class),
                contextBuilder,
                tools,
                toolCatalog,
                mock(TaskIntentScopeStore.class));
        RunExecutionContext context = context("搜索北京天气资料");
        RenderedPrompt prompt = new RenderedPrompt(
                "loop.execution",
                "v2",
                "system",
                "user",
                "hash");
        ModelProvider provider = mock(ModelProvider.class);
        RuntimeTransactionService.ExternalActionHandle handle =
                new RuntimeTransactionService.ExternalActionHandle(
                        UUID.randomUUID());
        LoopEvaluation evaluation = new LoopEvaluation(
                LoopEvaluationDecision.COMPLETE,
                "工具结果满足目标",
                "");
        LoopOutcome expected = LoopOutcome.completed(
                context,
                "搜索完成",
                UUID.randomUUID());
        RunExecutionContext child = childContext(context, 1);
        when(capabilities.prepare(context))
                .thenReturn(CapabilityPlanningContext.empty());
        when(contextBuilder.build(eq(context), any()))
                .thenReturn(new LoopContextSnapshot(
                        context.taskRunId(),
                        context.loopNodeId(),
                        List.of(),
                        1024));
        when(modelProviders.require("fake")).thenReturn(provider);
        when(provider.supportsNativeToolCalling()).thenReturn(true);
        when(provider.supportsNativeToolCalling(anyString())).thenReturn(true);
        when(toolCatalog.modelToolSpecs()).thenReturn(List.of(
                new ModelToolSpec(
                        "web.search",
                        "web_search",
                        "搜索网络",
                        "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}")));
        when(promptRegistry.render(any(), any())).thenReturn(prompt);
        when(transactions.startExternalAction(context, prompt))
                .thenReturn(handle);
        when(provider.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(
                        "fake",
                        "fake-model",
                        "",
                        "tool_calls",
                        List.of(new ModelToolCall(
                                "call-1",
                                "web.search",
                                "web_search",
                                Map.of("query", "北京天气"))),
                        ""));
        when(tools.invokeForLoop(
                eq(child),
                any(ToolInvocationCommand.class),
                eq(LoopActionType.WEB_SEARCH)))
                .thenReturn(new LoopActionResult(
                        LoopActionType.WEB_SEARCH,
                        "tool:web",
                        "北京天气搜索结果",
                        Map.of()));
        when(transactions.spawnChild(
                eq(context),
                any(ExecutionDerivationRequest.class),
                any(LoopExecutionPolicy.class))).thenReturn(child);
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

        LoopOutcome actual = service.execute(context);

        var requestCaptor = forClass(ModelRequest.class);
        verify(provider).generate(requestCaptor.capture());
        assertThat(actual).isSameAs(expected);
        assertThat(requestCaptor.getValue().tools())
                .extracting(ModelToolSpec::toolId)
                .containsExactly("web.search");
        verify(actionPlanner, never()).plan(any(), any(), any());
        verify(tools).invokeForLoop(
                eq(child),
                any(ToolInvocationCommand.class),
                eq(LoopActionType.WEB_SEARCH));
        verify(transactions).completeChildLoopNode(
                eq(child),
                any(LoopActionResult.class));
        verify(transactions).resumeAfterChildExecution(context);
    }

    @Test
    void nativeToolCallingExecutesAllToolCallsFromOneModelResponse() {
        RuntimeTransactionService transactions =
                mock(RuntimeTransactionService.class);
        ModelProviderRegistry modelProviders =
                mock(ModelProviderRegistry.class);
        PromptRegistry promptRegistry = mock(PromptRegistry.class);
        ReActActionPlanner actionPlanner = mock(ReActActionPlanner.class);
        CapabilityApplicationService capabilities =
                mock(CapabilityApplicationService.class);
        LoopContextBuilder contextBuilder = mock(LoopContextBuilder.class);
        ToolExecutionService tools = mock(ToolExecutionService.class);
        ToolCatalogService toolCatalog = mock(ToolCatalogService.class);
        LoopCompletionPolicy completionPolicy =
                mock(LoopCompletionPolicy.class);
        RuntimeExecutionService service = new RuntimeExecutionService(
                transactions,
                modelProviders,
                promptRegistry,
                actionPlanner,
                completionPolicy,
                new LoopCorrectionPolicy(),
                new ClarificationNeedDetector(),
                mock(ExecutionDerivationPolicy.class),
                capabilities,
                mock(RuntimeLeaseService.class),
                contextBuilder,
                tools,
                toolCatalog,
                mock(TaskIntentScopeStore.class));
        RunExecutionContext context = context("搜索北京天气资料");
        RenderedPrompt prompt = new RenderedPrompt(
                "loop.execution",
                "v2",
                "system",
                "user",
                "hash");
        ModelProvider provider = mock(ModelProvider.class);
        RuntimeTransactionService.ExternalActionHandle handle =
                new RuntimeTransactionService.ExternalActionHandle(
                        UUID.randomUUID());
        LoopEvaluation evaluation = new LoopEvaluation(
                LoopEvaluationDecision.COMPLETE,
                "批量搜索结果满足目标",
                "");
        LoopOutcome expected = LoopOutcome.completed(
                context,
                "搜索完成",
                UUID.randomUUID());
        RunExecutionContext child1 = childContext(context, 1);
        RunExecutionContext child2 = childContext(context, 2);
        when(capabilities.prepare(context))
                .thenReturn(CapabilityPlanningContext.empty());
        when(contextBuilder.build(eq(context), any()))
                .thenReturn(new LoopContextSnapshot(
                        context.taskRunId(),
                        context.loopNodeId(),
                        List.of(),
                        1024));
        when(modelProviders.require("fake")).thenReturn(provider);
        when(provider.supportsNativeToolCalling(anyString())).thenReturn(true);
        when(toolCatalog.modelToolSpecs()).thenReturn(List.of(
                new ModelToolSpec(
                        "web.search",
                        "web_search",
                        "搜索网络",
                        "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}")));
        when(promptRegistry.render(any(), any())).thenReturn(prompt);
        when(transactions.startExternalAction(context, prompt))
                .thenReturn(handle);
        when(provider.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(
                        "fake",
                        "fake-model",
                        "",
                        "tool_calls",
                        List.of(
                                new ModelToolCall(
                                        "call-1",
                                        "web.search",
                                        "web_search",
                                        Map.of("query", "java security")),
                                new ModelToolCall(
                                        "call-2",
                                        "web.search",
                                        "web_search",
                                        Map.of("query", "java authz"))),
                        ""));
        when(tools.invokeForLoop(
                any(RunExecutionContext.class),
                any(ToolInvocationCommand.class),
                eq(LoopActionType.WEB_SEARCH)))
                .thenReturn(
                        new LoopActionResult(
                                LoopActionType.WEB_SEARCH,
                                "tool:first",
                                "第一组搜索结果",
                                Map.of("success", true, "toolId", "web.search")),
                        new LoopActionResult(
                                LoopActionType.WEB_SEARCH,
                                "tool:second",
                                "第二组搜索结果",
                                Map.of("success", true, "toolId", "web.search")));
        when(transactions.spawnChild(
                eq(context),
                any(ExecutionDerivationRequest.class),
                any(LoopExecutionPolicy.class))).thenReturn(child1, child2);
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

        LoopOutcome actual = service.execute(context);

        var resultCaptor = forClass(LoopActionResult.class);
        verify(tools, times(2)).invokeForLoop(
                any(RunExecutionContext.class),
                any(ToolInvocationCommand.class),
                eq(LoopActionType.WEB_SEARCH));
        verify(transactions, times(2)).completeChildLoopNode(
                any(RunExecutionContext.class),
                any(LoopActionResult.class));
        verify(transactions).resumeAfterChildExecution(context);
        verify(completionPolicy).evaluate(
                eq(context),
                resultCaptor.capture(),
                any(LoopExecutionPolicy.class),
                eq(1));
        LoopActionResult result = resultCaptor.getValue();
        assertThat(actual).isSameAs(expected);
        assertThat(result.actionType()).isEqualTo(LoopActionType.WEB_SEARCH);
        assertThat(result.attributes())
                .containsEntry("toolBatch", true)
                .containsEntry("toolCallCount", 2)
                .containsEntry("toolId", "web.search");
        assertThat(result.content())
                .contains("第一组搜索结果")
                .contains("第二组搜索结果");
    }

    @Test
    void nativeToolObservationIsNeverUpgradedToClarification() {
        RuntimeTransactionService transactions =
                mock(RuntimeTransactionService.class);
        ModelProviderRegistry modelProviders =
                mock(ModelProviderRegistry.class);
        PromptRegistry promptRegistry = mock(PromptRegistry.class);
        ReActActionPlanner actionPlanner = mock(ReActActionPlanner.class);
        CapabilityApplicationService capabilities =
                mock(CapabilityApplicationService.class);
        LoopContextBuilder contextBuilder = mock(LoopContextBuilder.class);
        ToolExecutionService tools = mock(ToolExecutionService.class);
        ToolCatalogService toolCatalog = mock(ToolCatalogService.class);
        LoopCompletionPolicy completionPolicy =
                mock(LoopCompletionPolicy.class);
        RuntimeExecutionService service = new RuntimeExecutionService(
                transactions,
                modelProviders,
                promptRegistry,
                actionPlanner,
                completionPolicy,
                new LoopCorrectionPolicy(),
                new ClarificationNeedDetector(),
                mock(ExecutionDerivationPolicy.class),
                capabilities,
                mock(RuntimeLeaseService.class),
                contextBuilder,
                tools,
                toolCatalog,
                mock(TaskIntentScopeStore.class));
        RunExecutionContext context = context();
        RenderedPrompt prompt = new RenderedPrompt(
                "loop.execution",
                "v2",
                "system",
                "user",
                "hash");
        ModelProvider provider = mock(ModelProvider.class);
        RuntimeTransactionService.ExternalActionHandle handle =
                new RuntimeTransactionService.ExternalActionHandle(
                        UUID.randomUUID());
        LoopEvaluation evaluation = new LoopEvaluation(
                LoopEvaluationDecision.COMPLETE,
                "工具 Observation 已交给 LoopCompletionPolicy 处理",
                "");
        LoopOutcome expected = LoopOutcome.completed(
                context,
                "已完成",
                UUID.randomUUID());
        RunExecutionContext child = childContext(context, 1);
        when(capabilities.prepare(context))
                .thenReturn(CapabilityPlanningContext.empty());
        when(contextBuilder.build(eq(context), any()))
                .thenReturn(new LoopContextSnapshot(
                        context.taskRunId(),
                        context.loopNodeId(),
                        List.of(),
                        1024));
        when(modelProviders.require("fake")).thenReturn(provider);
        when(provider.supportsNativeToolCalling(anyString())).thenReturn(true);
        when(toolCatalog.modelToolSpecs()).thenReturn(List.of(
                new ModelToolSpec(
                        "web.search",
                        "web_search",
                        "搜索网络",
                        "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}")));
        when(promptRegistry.render(any(), any())).thenReturn(prompt);
        when(transactions.startExternalAction(context, prompt))
                .thenReturn(handle);
        when(provider.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(
                        "fake",
                        "fake-model",
                        "",
                        "tool_calls",
                        List.of(new ModelToolCall(
                                "call-1",
                                "web.search",
                                "web_search",
                                Map.of("query", "java security"))),
                        ""));
        when(tools.invokeForLoop(
                eq(child),
                any(ToolInvocationCommand.class),
                eq(LoopActionType.WEB_SEARCH)))
                .thenReturn(new LoopActionResult(
                        LoopActionType.WEB_SEARCH,
                        "tool:web",
                        "请补充姓名、角色、用途和背景。",
                        Map.of("success", true, "toolId", "web.search")));
        when(transactions.spawnChild(
                eq(context),
                any(ExecutionDerivationRequest.class),
                any(LoopExecutionPolicy.class))).thenReturn(child);
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

        LoopOutcome actual = service.execute(context);

        assertThat(actual).isSameAs(expected);
        verify(tools, never()).invokeForLoop(
                eq(context),
                any(ToolInvocationCommand.class),
                eq(LoopActionType.CLARIFICATION_REQUEST));
        verify(completionPolicy).evaluate(
                eq(context),
                any(LoopActionResult.class),
                any(LoopExecutionPolicy.class),
                eq(1));
    }

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
        ReActActionPlanner actionPlanner = mock(ReActActionPlanner.class);
        RuntimeExecutionService service = new RuntimeExecutionService(
                transactions,
                modelProviders,
                promptRegistry,
                actionPlanner,
                mock(LoopCompletionPolicy.class),
                new LoopCorrectionPolicy(),
                new ClarificationNeedDetector(),
                mock(ExecutionDerivationPolicy.class),
                capabilities,
                mock(RuntimeLeaseService.class),
                contextBuilder,
                tools,
                mock(ToolCatalogService.class),
                mock(TaskIntentScopeStore.class));
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
        when(actionPlanner.plan(eq(context), any(), any()))
                .thenReturn(LoopPlan.modelCall(
                        "Provider 返回非空、可展示的最终结果",
                        "调用模型完成当前目标",
                        512));
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
        when(tools.invokeForLoop(
                eq(context),
                any(),
                eq(LoopActionType.CLARIFICATION_REQUEST)))
                .thenReturn(new LoopActionResult(
                        LoopActionType.CLARIFICATION_REQUEST,
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
        verify(tools).invokeForLoop(
                eq(context),
                commandCaptor.capture(),
                eq(LoopActionType.CLARIFICATION_REQUEST));
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
                mock(ReActActionPlanner.class),
                completionPolicy,
                new LoopCorrectionPolicy(),
                mock(ClarificationNeedDetector.class),
                mock(ExecutionDerivationPolicy.class),
                mock(CapabilityApplicationService.class),
                mock(RuntimeLeaseService.class),
                mock(LoopContextBuilder.class),
                mock(ToolExecutionService.class),
                mock(ToolCatalogService.class),
                mock(TaskIntentScopeStore.class));
    }

    /** @return 测试用 Child LoopNode 上下文 */
    private RunExecutionContext childContext(
            RunExecutionContext parent,
            int sequence) {
        return new RunExecutionContext(
                parent.jobId(),
                parent.taskId(),
                parent.taskRunId(),
                parent.loopRunId(),
                UUID.randomUUID(),
                parent.loopNodeId(),
                parent.depth() + 1,
                parent.iterationNo() + sequence,
                parent.loopRunParentType(),
                parent.loopRunParentId(),
                parent.recursionDepth(),
                parent.providerId(),
                "执行工具调用",
                "继承父节点模型 tool_call 决策",
                null);
    }

    /** @return 测试用 LoopNode 上下文 */
    private RunExecutionContext context() {
        return context("生成个人简历");
    }

    /** @return 测试用 LoopNode 上下文 */
    private RunExecutionContext context(String goal) {
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
                goal,
                "",
                null);
    }
}
