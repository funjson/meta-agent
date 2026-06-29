package com.funjson.metaagent.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.capability.domain.CapabilityPlanningContext;
import com.funjson.metaagent.capability.domain.ScopedCapabilityContext;
import com.funjson.metaagent.context.domain.LoopContextSnapshot;
import com.funjson.metaagent.loop.application.ReActActionPlanner;
import com.funjson.metaagent.loop.domain.ExecutionDerivationRequest;
import com.funjson.metaagent.loop.domain.LoopActionType;
import com.funjson.metaagent.loop.domain.LoopRunParentType;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.prompt.application.PromptRegistry;
import com.funjson.metaagent.prompt.domain.RenderedPrompt;
import com.funjson.metaagent.prompt.domain.PromptUseCase;
import com.funjson.metaagent.provider.application.ModelProviderRegistry;
import com.funjson.metaagent.provider.domain.ModelProvider;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.provider.domain.ModelResponse;
import com.funjson.metaagent.runtime.domain.CapabilityRequest;
import com.funjson.metaagent.runtime.domain.ChildJobRequest;
import com.funjson.metaagent.runtime.domain.ContractContribution;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.runtime.domain.TaskGraphTemplateRef;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 验证 ReActActionPlanner 的结构化动作选择和降级边界。
 */
class ReActActionPlannerTest {

    @Test
    void modelPlannerParsesFileReadToolCall() {
        ModelProviderRegistry providers = Mockito.mock(
                ModelProviderRegistry.class);
        PromptRegistry prompts = Mockito.mock(PromptRegistry.class);
        ModelProvider provider = Mockito.mock(ModelProvider.class);
        when(prompts.render(any(PromptUseCase.class), any()))
                .thenReturn(prompt());
        when(providers.require("fake")).thenReturn(provider);
        when(provider.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(
                        "fake",
                        "planner",
                        """
                        {
                          "actionType": "TOOL_CALL",
                          "toolId": "file.read",
                          "summary": "读取用户上传的文件",
                          "completionCriterion": "文件正文进入 Observation",
                          "arguments": {
                            "fileId": "67cbd604-2d0e-4373-8d89-abf368a213cb",
                            "maxChars": 20000
                          }
                        }
                        """,
                        "stop"));
        ReActActionPlanner planner = new ReActActionPlanner(
                providers,
                prompts,
                new ObjectMapper());

        var plan = planner.plan(
                context("根据上传文件回答", ""),
                CapabilityPlanningContext.empty(),
                snapshot());

        assertThat(plan.actionType()).isEqualTo(LoopActionType.TOOL_CALL);
        assertThat(plan.toolId()).isEqualTo("file.read");
        assertThat(plan.toolArguments())
                .containsEntry(
                        "fileId",
                        "67cbd604-2d0e-4373-8d89-abf368a213cb");
    }

    @Test
    void repeatedWebSearchAfterObservationConvergesToModelCall() {
        ModelProviderRegistry providers = Mockito.mock(
                ModelProviderRegistry.class);
        PromptRegistry prompts = Mockito.mock(PromptRegistry.class);
        ModelProvider provider = Mockito.mock(ModelProvider.class);
        when(prompts.render(any(PromptUseCase.class), any()))
                .thenReturn(prompt());
        when(providers.require("fake")).thenReturn(provider);
        when(provider.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(
                        "fake",
                        "planner",
                        """
                        {
                          "actionType": "WEB_SEARCH",
                          "toolId": "web.search",
                          "summary": "继续搜索北京天气",
                          "completionCriterion": "搜索结果进入 Observation",
                          "arguments": {
                            "query": "北京最近一周天气预报"
                          }
                        }
                        """,
                        "stop"));
        ReActActionPlanner planner = new ReActActionPlanner(
                providers,
                prompts,
                new ObjectMapper());

        var plan = planner.plan(
                context(
                        "帮我看一下北京最近一周天气如何",
                        "上一轮工具动作 WEB_SEARCH 返回：已有北京天气搜索结果。"),
                CapabilityPlanningContext.empty(),
                snapshot());

        assertThat(plan.actionType()).isEqualTo(LoopActionType.MODEL_CALL);
        assertThat(plan.summary()).contains("WEB_SEARCH Observation");
    }

    @Test
    void modelPlannerParsesClarificationAction() {
        ModelProviderRegistry providers = Mockito.mock(
                ModelProviderRegistry.class);
        PromptRegistry prompts = Mockito.mock(PromptRegistry.class);
        ModelProvider provider = Mockito.mock(ModelProvider.class);
        when(prompts.render(any(PromptUseCase.class), any()))
                .thenReturn(prompt());
        when(providers.require("fake")).thenReturn(provider);
        when(provider.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(
                        "fake",
                        "planner",
                        """
                        {
                          "actionType": "CLARIFICATION_REQUEST",
                          "summary": "缺少关键输入",
                          "completionCriterion": "用户回答后恢复",
                          "arguments": {
                            "question": "请补充用途和风格。"
                          }
                        }
                        """,
                        "stop"));
        ReActActionPlanner planner = new ReActActionPlanner(
                providers,
                prompts,
                new ObjectMapper());

        var plan = planner.plan(
                context("帮我写个人介绍", ""),
                CapabilityPlanningContext.empty(),
                snapshot());

        assertThat(plan.actionType())
                .isEqualTo(LoopActionType.CLARIFICATION_REQUEST);
        assertThat(plan.toolId()).isEqualTo("clarification.request");
        assertThat(plan.toolArguments())
                .containsEntry("question", "请补充用途和风格。");
    }

    @Test
    void childJobDerivationBypassesModelPlanner() {
        ReActActionPlanner planner = new ReActActionPlanner(
                Mockito.mock(ModelProviderRegistry.class),
                Mockito.mock(PromptRegistry.class),
                new ObjectMapper());
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

        var plan = planner.plan(
                context("parent goal", ""),
                new CapabilityPlanningContext(
                        ScopedCapabilityContext.empty(),
                        derivation),
                snapshot());

        assertThat(plan.actionType()).isEqualTo(LoopActionType.CHILD_JOB);
    }

    @Test
    void invalidModelPlanFailsTheLoop() {
        ModelProviderRegistry providers = Mockito.mock(
                ModelProviderRegistry.class);
        PromptRegistry prompts = Mockito.mock(PromptRegistry.class);
        ModelProvider provider = Mockito.mock(ModelProvider.class);
        when(prompts.render(any(PromptUseCase.class), any()))
                .thenReturn(prompt());
        when(providers.require("fake")).thenReturn(provider);
        when(provider.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(
                        "fake",
                        "planner",
                        "not json",
                        "stop"));
        ReActActionPlanner planner = new ReActActionPlanner(
                providers,
                prompts,
                new ObjectMapper());

        assertThatThrownBy(() -> planner.plan(
                        context("你好", ""),
                        CapabilityPlanningContext.empty(),
                        snapshot()))
                .isInstanceOf(RuntimeStateException.class)
                .hasMessageContaining("invalid ReAct action plan");
    }

    /** @return 测试 Prompt */
    private RenderedPrompt prompt() {
        return new RenderedPrompt(
                "loop.action-planning",
                "v1",
                "system",
                "user",
                "hash");
    }

    /** @return 测试上下文快照 */
    private LoopContextSnapshot snapshot() {
        UUID id = UUID.randomUUID();
        return new LoopContextSnapshot(
                id,
                UUID.randomUUID(),
                List.of(),
                1024);
    }

    /**
     * 创建测试执行上下文。
     *
     * @param goal 目标
     * @param feedback 调整反馈
     * @return Loop 上下文
     */
    private RunExecutionContext context(
            String goal,
            String feedback) {
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
                feedback,
                null);
    }
}
