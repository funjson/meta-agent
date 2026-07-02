package com.funjson.metaagent.loop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.loop.domain.LoopRunParentType;
import com.funjson.metaagent.loop.domain.LoopToolExposurePolicy;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.runtime.domain.TaskIntentScope;
import org.junit.jupiter.api.Test;

/**
 * Verifies task-scoped native tool exposure for mixed-turn execution.
 */
class LoopToolExposurePolicyTest {

    private final LoopToolExposurePolicy policy = new LoopToolExposurePolicy();

    @Test
    void pureTextGenerationDoesNotExposeWeatherOrWebTools() {
        RunExecutionContext context = context("生成一份个人介绍文本");

        assertThat(policy.allowNativeTool(context, "weather.current"))
                .isFalse();
        assertThat(policy.allowNativeTool(context, "web.search"))
                .isFalse();
        assertThat(policy.allowNativeTool(context, "file.read"))
                .isFalse();
    }

    @Test
    void weatherGoalExposesWeatherButNotGenericWebSearch() {
        RunExecutionContext context = context("查询北京今天的天气");

        assertThat(policy.allowNativeTool(context, "weather.current"))
                .isTrue();
        assertThat(policy.allowNativeTool(context, "web.search"))
                .isFalse();
    }

    @Test
    void researchGoalExposesWebToolsButNotWeatherTool() {
        RunExecutionContext context = context("调研 Java 安全架构最新资料");

        assertThat(policy.allowNativeTool(context, "web.search"))
                .isTrue();
        assertThat(policy.allowNativeTool(context, "web.fetch"))
                .isTrue();
        assertThat(policy.allowNativeTool(context, "weather.current"))
                .isFalse();
    }

    @Test
    void fileGoalExposesFileToolsOnly() {
        RunExecutionContext context = context("读取上传文件并总结内容");

        assertThat(policy.allowNativeTool(context, "file.read"))
                .isTrue();
        assertThat(policy.allowNativeTool(context, "web.search"))
                .isFalse();
        assertThat(policy.allowNativeTool(context, "weather.current"))
                .isFalse();
    }

    @Test
    void scopedAllowlistOverridesLegacyGoalHeuristic() {
        RunExecutionContext context = context("query today's weather");
        TaskIntentScope profileScope = scope(
                "RESUME_OR_PROFILE_GENERATION",
                List.of());

        assertThat(policy.allowNativeTool(context, profileScope, "weather.current"))
                .isFalse();
        assertThat(policy.allowNativeTool(context, profileScope, "web.search"))
                .isFalse();
    }

    @Test
    void scopedAllowlistExposesOnlyCurrentTaskTools() {
        RunExecutionContext context = context(
                "write a profile introduction and also check today's weather");
        TaskIntentScope weatherScope = scope(
                "WEATHER_QUERY",
                List.of("weather.current"));

        assertThat(policy.allowNativeTool(context, weatherScope, "weather.current"))
                .isTrue();
        assertThat(policy.allowNativeTool(context, weatherScope, "web.search"))
                .isFalse();
        assertThat(policy.allowNativeTool(context, weatherScope, "file.read"))
                .isFalse();
    }

    /**
     * Builds a task intent scope with a concrete tool allowlist.
     */
    private TaskIntentScope scope(String taskType, List<String> allowedTools) {
        return new TaskIntentScope(
                UUID.randomUUID().toString(),
                taskType,
                "",
                "",
                "",
                List.of(),
                "LOW",
                "{}",
                allowedTools,
                true);
    }

    /**
     * Builds a minimal RunExecutionContext for domain policy tests.
     */
    private RunExecutionContext context(String goal) {
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
                goal,
                "",
                null);
    }
}
