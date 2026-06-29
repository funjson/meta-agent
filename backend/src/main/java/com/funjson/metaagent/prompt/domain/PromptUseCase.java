package com.funjson.metaagent.prompt.domain;

import java.util.Set;

/**
 * 定义平台内受版本控制的 Prompt 用途。
 *
 * <p>枚举只保存稳定标识、版本和模板位置，不包含实际 Prompt 文本。实际内容位于
 * classpath 资源中，便于独立评审、版本比较和后续迁移到外部 Prompt 仓库。</p>
 */
public enum PromptUseCase {

    /** Control Kernel 的结构化意图识别 Prompt。 */
    CONTROL_INTENT_RECOGNITION(
            "control.intent-recognition",
            "v1",
            "classpath:prompts/control/intent-recognition/v1/system.md",
            "classpath:prompts/control/intent-recognition/v1/user.md",
            Set.of("conversationContext", "userMessage")),

    /** Control Kernel 的等待交互结构化路由 Prompt。 */
    CONTROL_PENDING_INTERACTION_ROUTING(
            "control.pending-interaction-routing",
            "v1",
            "classpath:prompts/control/pending-interaction-routing/v1/system.md",
            "classpath:prompts/control/pending-interaction-routing/v1/user.md",
            Set.of(
                    "conversationContext",
                    "candidateJson",
                    "userMessage")),

    /** Control Kernel 的复合 Task Graph 规划 Prompt。 */
    CONTROL_TASK_GRAPH(
            "control.task-graph",
            "v1",
            "classpath:prompts/control/task-graph/v1/system.md",
            "classpath:prompts/control/task-graph/v1/user.md",
            Set.of(
                    "goalSummary",
                    "constraints",
                    "userRequest")),

    /** Loop Kernel 执行当前任务目标的 Prompt。 */
    LOOP_EXECUTION(
            "loop.execution",
            "v2",
            "classpath:prompts/loop/execution/v2/system.md",
            "classpath:prompts/loop/execution/v2/user.md",
            Set.of(
                    "goal",
                    "contextSummary",
                    "iterationNo",
                    "feedback")),

    /** Loop Kernel 在 ReAct Planning 阶段选择下一步结构化动作的 Prompt。 */
    LOOP_ACTION_PLANNING(
            "loop.action-planning",
            "v1",
            "classpath:prompts/loop/action-planning/v1/system.md",
            "classpath:prompts/loop/action-planning/v1/user.md",
            Set.of(
                    "goal",
                    "contextSummary",
                    "capabilitySummary",
                    "feedback")),

    /** Skill 导入时的不可变 Manifest 编译 Prompt。 */
    SKILL_COMPILATION(
            "capability.skill-compilation",
            "v1",
            "classpath:prompts/capability/skill-compilation/v1/system.md",
            "classpath:prompts/capability/skill-compilation/v1/user.md",
            Set.of(
                    "sourceId",
                    "sourceVersion",
                    "rawContent")),

    /** Provider 配置页使用的最小连接测试 Prompt。 */
    PROVIDER_CONNECTION_TEST(
            "provider.connection-test",
            "v1",
            "classpath:prompts/provider/connection-test/v1/system.md",
            "classpath:prompts/provider/connection-test/v1/user.md",
            Set.of());

    private final String id;
    private final String version;
    private final String systemResource;
    private final String userResource;
    private final Set<String> requiredVariables;

    /**
     * 创建 Prompt 用途定义。
     *
     * @param id 稳定 Prompt 标识
     * @param version Prompt 版本
     * @param systemResource system 模板资源
     * @param userResource user 模板资源
     * @param requiredVariables 必填模板变量
     */
    PromptUseCase(
            String id,
            String version,
            String systemResource,
            String userResource,
            Set<String> requiredVariables) {
        this.id = id;
        this.version = version;
        this.systemResource = systemResource;
        this.userResource = userResource;
        this.requiredVariables = Set.copyOf(requiredVariables);
    }

    /**
     * 返回稳定 Prompt 标识。
     *
     * @return Prompt 标识
     */
    public String id() {
        return id;
    }

    /**
     * 返回 Prompt 版本。
     *
     * @return 版本
     */
    public String version() {
        return version;
    }

    /**
     * 返回 system 模板资源位置。
     *
     * @return classpath 资源位置
     */
    public String systemResource() {
        return systemResource;
    }

    /**
     * 返回 user 模板资源位置。
     *
     * @return classpath 资源位置
     */
    public String userResource() {
        return userResource;
    }

    /**
     * 返回渲染时必须提供的变量。
     *
     * @return 不可变变量集合
     */
    public Set<String> requiredVariables() {
        return requiredVariables;
    }
}
