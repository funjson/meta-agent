package com.funjson.metaagent.job.application;

import java.util.List;
import java.util.Locale;

import com.funjson.metaagent.job.domain.TaskGraphNodePlan;
import com.funjson.metaagent.job.domain.TaskGraphPlan;
import com.funjson.metaagent.job.domain.TaskGraphValidator;
import com.funjson.metaagent.task.domain.TaskStatus;
import org.springframework.stereotype.Service;

/**
 * Builds the framework-owned default TaskGraph for Deep Research jobs.
 *
 * <p>This is not a new Workflow abstraction. It is a Job-layer fallback
 * template used only when the user intent explicitly asks for deep research
 * and no configured {@code TaskGraphTemplate} matched the AgentProfile.</p>
 */
@Service
public class DefaultResearchTaskGraphFactory {

    /** Stable source tag used for audit and smoke tests. */
    public static final String SOURCE = "DEFAULT_DEEP_RESEARCH_TASK_GRAPH";

    private final TaskGraphValidator validator;

    /**
     * Creates the factory.
     *
     * @param validator TaskGraph invariant validator
     */
    public DefaultResearchTaskGraphFactory(TaskGraphValidator validator) {
        this.validator = validator;
    }

    /**
     * Checks whether labels require the default Deep Research graph.
     *
     * @param labels intent labels
     * @return true when labels explicitly request deep research
     */
    public boolean supports(List<String> labels) {
        return labels != null && labels.stream()
                .map(label -> label.toLowerCase(Locale.ROOT).trim())
                .anyMatch("research-depth:deep-research"::equals);
    }

    /**
     * Creates a validated Deep Research TaskGraph.
     *
     * @param originalRequest raw user request
     * @param goalSummary recognized goal summary
     * @param constraints recognized user constraints
     * @return validated TaskGraph
     */
    public TaskGraphPlan create(
            String originalRequest,
            String goalSummary,
            List<String> constraints) {
        String sharedContext = sharedContext(
                originalRequest,
                goalSummary,
                constraints);
        return validator.validate(new TaskGraphPlan(
                SOURCE,
                "默认 Deep Research TaskGraph：计划、发现来源、读取证据、构建证据矩阵、合成报告并复核。",
                List.of(
                        node(
                                "research-plan",
                                "研究计划与问题拆解",
                                """
                                %s

                                形成研究计划：明确研究问题、范围边界、需要验证的子问题、
                                搜索关键词策略和预期证据类型。不要直接输出最终报告。
                                """.formatted(sharedContext).trim(),
                                List.of()),
                        node(
                                "source-discovery",
                                "多查询来源发现",
                                """
                                %s

                                基于前置研究计划执行 web.search。需要覆盖不同查询角度，
                                记录候选来源，优先选择官方、权威媒体、文档或一手资料。
                                输出候选来源选择理由和需要继续读取的 URL。
                                """.formatted(sharedContext).trim(),
                                List.of("research-plan")),
                        node(
                                "source-reading",
                                "来源读取与证据抽取",
                                """
                                %s

                                基于前置候选来源使用 web.fetch 或 web.extract 读取页面。
                                必须把关键事实、数字、时间和来源 URL 写入证据池；
                                搜索摘要不能当成最终证据。
                                """.formatted(sharedContext).trim(),
                                List.of("source-discovery")),
                        node(
                                "evidence-matrix",
                                "证据矩阵与冲突整理",
                                """
                                %s

                                基于前置读取结果整理证据矩阵：主张、支持证据、来源、
                                置信度、冲突点和信息缺口。不要引入未被证据支持的结论。
                                """.formatted(sharedContext).trim(),
                                List.of("source-reading")),
                        node(
                                "report-synthesis",
                                "结构化研究报告",
                                """
                                %s

                                基于证据矩阵合成面向用户的研究报告。报告需要包含结论、
                                依据、引用来源、局限性和后续建议；引用必须来自已读取来源。
                                """.formatted(sharedContext).trim(),
                                List.of("evidence-matrix")),
                        node(
                                "quality-review",
                                "引用与结论复核",
                                """
                                %s

                                复核前置报告是否满足原始目标、是否引用已读取来源、
                                是否区分事实/推断/不确定信息。发现问题时给出修订后的最终答复。
                                """.formatted(sharedContext).trim(),
                                List.of("report-synthesis"))),
                null));
    }

    /**
     * Creates one TaskGraph node with dependency-derived initial status.
     */
    private TaskGraphNodePlan node(
            String key,
            String title,
            String goal,
            List<String> dependsOn) {
        return new TaskGraphNodePlan(
                key,
                title,
                goal,
                dependsOn.isEmpty() ? TaskStatus.READY : TaskStatus.BLOCKED,
                "LOOP",
                dependsOn);
    }

    /**
     * Renders stable shared context injected into every research task.
     */
    private String sharedContext(
            String originalRequest,
            String goalSummary,
            List<String> constraints) {
        String normalizedConstraints = constraints == null
                || constraints.isEmpty()
                        ? "无额外约束"
                        : String.join("；", constraints);
        return """
                Job 原始请求：
                %s

                Job 目标摘要：
                %s

                用户约束：
                %s
                """.formatted(
                        safeText(originalRequest),
                        safeText(goalSummary),
                        normalizedConstraints).trim();
    }

    /**
     * Normalizes nullable text for prompt-safe task goals.
     */
    private String safeText(String value) {
        return value == null || value.isBlank() ? "未提供" : value.trim();
    }
}
