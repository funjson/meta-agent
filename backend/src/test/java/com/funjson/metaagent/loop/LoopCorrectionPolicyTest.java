package com.funjson.metaagent.loop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import com.funjson.metaagent.loop.domain.LoopActionType;
import com.funjson.metaagent.loop.domain.LoopCorrectionPolicy;
import com.funjson.metaagent.loop.domain.LoopPlan;
import com.funjson.metaagent.loop.domain.LoopRunParentType;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests for deterministic ReAct correction around web research tools.
 */
class LoopCorrectionPolicyTest {

    private final LoopCorrectionPolicy policy = new LoopCorrectionPolicy();

    @Test
    void allowsInitialWebSearchWithoutPreviousObservation() {
        assertThat(policy.allowNativeTool(context(""), "web.search"))
                .isTrue();
    }

    @Test
    void allowsFetchAndExtractButBlocksRepeatedSearchAfterSearch() {
        RunExecutionContext context = context(
                "上一轮工具动作 WEB_SEARCH（toolId=web.search） 返回：候选来源。");

        assertThat(policy.allowNativeTool(context, "web.fetch")).isTrue();
        assertThat(policy.allowNativeTool(context, "web.extract")).isTrue();
        assertThat(policy.allowNativeTool(context, "web.search")).isFalse();
    }

    @Test
    void allowsExtractButBlocksSearchAndFetchAfterFetch() {
        RunExecutionContext context = context(
                "上一轮工具动作 TOOL_CALL（toolId=web.fetch） 返回：页面正文。");

        assertThat(policy.allowNativeTool(context, "web.extract")).isTrue();
        assertThat(policy.allowNativeTool(context, "web.fetch")).isFalse();
        assertThat(policy.allowNativeTool(context, "web.search")).isFalse();
    }

    @Test
    void blocksWebToolsAfterEvidenceExtraction() {
        RunExecutionContext context = context(
                "上一轮工具动作 TOOL_CALL（toolId=web.extract） 返回：证据片段。");

        assertThat(policy.allowNativeTool(context, "web.search")).isFalse();
        assertThat(policy.allowNativeTool(context, "web.fetch")).isFalse();
        assertThat(policy.allowNativeTool(context, "web.extract")).isFalse();
    }

    @Test
    void correctPlanConvergesRepeatedSearchToModelCall() {
        LoopPlan repeatedSearch = LoopPlan.toolCall(
                LoopActionType.WEB_SEARCH,
                "返回候选来源",
                "再次搜索",
                "web.search",
                Map.of("query", "北京天气"));

        LoopPlan corrected = policy.correctPlan(
                context("上一轮工具动作 WEB_SEARCH（toolId=web.search） 返回：候选来源。"),
                repeatedSearch);

        assertThat(corrected.actionType()).isEqualTo(LoopActionType.MODEL_CALL);
    }

    @Test
    void correctPlanKeepsFetchAfterSearch() {
        LoopPlan fetch = LoopPlan.toolCall(
                LoopActionType.TOOL_CALL,
                "读取候选来源",
                "读取第一页",
                "web.fetch",
                Map.of("url", "https://example.com"));

        LoopPlan corrected = policy.correctPlan(
                context("上一轮工具动作 WEB_SEARCH（toolId=web.search） 返回：候选来源。"),
                fetch);

        assertThat(corrected).isSameAs(fetch);
    }

    /**
     * Builds a minimal RunExecutionContext for domain policy tests.
     */
    private RunExecutionContext context(String feedback) {
        return new RunExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                0,
                1,
                LoopRunParentType.TASK_RUN,
                UUID.randomUUID(),
                0,
                "fake-chat",
                "goal",
                feedback,
                null);
    }
}
