package com.funjson.metaagent.loop.domain;

import java.util.Locale;

import com.funjson.metaagent.runtime.domain.TaskIntentScope;

/**
 * Decides which native tools are visible to one LoopNode.
 *
 * <p>The model should not see every framework tool on every task. In a mixed
 * turn such as "write my introduction and check the weather", sibling Jobs
 * share the same Conversation facts, but each LoopNode must still execute only
 * the current Task goal. This policy keeps tool exposure scoped to the local
 * Loop goal before the model can choose a native function call.</p>
 */
public class LoopToolExposurePolicy {

    /**
     * Checks whether a native tool is semantically available for the current
     * LoopNode.
     *
     * @param context current Loop execution context
     * @param toolId candidate framework tool ID
     * @return true when the tool can be exposed to the model
     */
    public boolean allowNativeTool(
            RunExecutionContext context,
            String toolId) {
        return allowNativeTool(context, TaskIntentScope.unspecified(), toolId);
    }

    /**
     * Checks whether a native tool is semantically available for the current
     * LoopNode under a persisted task intent scope.
     *
     * @param context current Loop execution context
     * @param scope Job-scoped intent snapshot
     * @param toolId candidate framework tool ID
     * @return true when the tool can be exposed to the model
     */
    public boolean allowNativeTool(
            RunExecutionContext context,
            TaskIntentScope scope,
            String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return false;
        }
        String candidate = toolId.trim().toLowerCase(Locale.ROOT);
        if ("clarification.request".equals(candidate)) {
            return false;
        }
        if (scope != null && scope.specified()) {
            // Formal Jobs carry a task-level allowlist. This is the primary
            // isolation boundary that prevents mixed-turn sibling Jobs from
            // sharing tools after they have already been split.
            return scope.allowsTool(candidate);
        }
        String signal = taskSignal(context);
        if (candidate.startsWith("weather.")) {
            return hasWeatherLookupSignal(signal) && !hasResearchSignal(signal);
        }
        if (candidate.startsWith("web.")) {
            return hasWebSignal(signal)
                    && !(hasWeatherLookupSignal(signal)
                    && !hasResearchSignal(signal));
        }
        if (candidate.startsWith("file.")) {
            return hasFileSignal(signal);
        }
        if (candidate.startsWith("skill.")) {
            return hasSkillSignal(signal);
        }
        if (candidate.startsWith("rag.")) {
            return hasKnowledgeSignal(signal);
        }
        return hasExplicitToolSignal(signal);
    }

    /**
     * Builds the task-local signal used for tool exposure.
     */
    private String taskSignal(RunExecutionContext context) {
        if (context == null) {
            return "";
        }
        return normalize("%s\n%s".formatted(
                safe(context.goal()),
                safe(context.feedback())));
    }

    /**
     * Detects weather lookup tasks that should use the weather tool directly.
     */
    private boolean hasWeatherLookupSignal(String signal) {
        return containsAny(
                signal,
                "weather",
                "forecast",
                "temperature",
                "天气",
                "气温",
                "温度",
                "降水",
                "下雨",
                "空气质量",
                "风力",
                "湿度");
    }

    /**
     * Detects web/research tasks that need public external sources.
     */
    private boolean hasWebSignal(String signal) {
        return containsAny(
                signal,
                "web",
                "search",
                "research",
                "deep research",
                "deep-research",
                "source",
                "citation",
                "搜索",
                "检索",
                "调研",
                "研究",
                "资料",
                "来源",
                "引用",
                "新闻",
                "官网",
                "政策",
                "价格",
                "版本",
                "最新");
    }

    /**
     * Detects tasks that explicitly need file access.
     */
    private boolean hasFileSignal(String signal) {
        return containsAny(
                signal,
                "file",
                "pdf",
                "word",
                "excel",
                "csv",
                "attachment",
                "文件",
                "附件",
                "上传",
                "文档",
                "表格",
                "读取",
                "写入");
    }

    /**
     * Detects tasks that explicitly ask for Skill discovery or loading.
     */
    private boolean hasSkillSignal(String signal) {
        return containsAny(
                signal,
                "skill",
                "capability",
                "技能",
                "能力");
    }

    /**
     * Detects knowledge-base oriented retrieval tasks.
     */
    private boolean hasKnowledgeSignal(String signal) {
        return containsAny(
                signal,
                "rag",
                "knowledge",
                "knowledge base",
                "知识库",
                "资料库",
                "检索库");
    }

    /**
     * Detects explicit tool execution requests for future custom tools.
     */
    private boolean hasExplicitToolSignal(String signal) {
        return containsAny(
                signal,
                "tool",
                "function",
                "工具",
                "函数");
    }

    /**
     * Detects research intent that should prefer web tools over current
     * weather lookup.
     */
    private boolean hasResearchSignal(String signal) {
        return containsAny(
                signal,
                "research",
                "deep research",
                "deep-research",
                "study",
                "analysis",
                "调研",
                "研究",
                "分析",
                "报告",
                "资料",
                "来源",
                "引用");
    }

    /**
     * Checks whether text contains any candidate token.
     */
    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalizes a text signal for deterministic matching.
     */
    private String normalize(String value) {
        return safe(value)
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
    }

    /**
     * Converts null text to an empty string.
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }
}
