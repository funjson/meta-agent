package com.funjson.metaagent.conversation.application;

import org.springframework.stereotype.Service;

/**
 * 把执行层结果转换为可展示给用户的自然回复。
 *
 * <p>Loop/Task 可以保留内部术语供 Agent Path 审计，但聊天消息不能泄露这些
 * 框架内部对象和调度细节。</p>
 */
@Service
public class UserFacingResponseRenderer {

    /**
     * 渲染最终聊天回复。
     *
     * @param rawResult TaskRun 原始结果
     * @return 用户可见回复
     */
    public String render(String rawResult) {
        if (rawResult == null || rawResult.isBlank()) {
            return "我已经完成了这一步，但没有生成可展示的文本结果。";
        }
        String normalized = rawResult.trim();
        StringBuilder cleaned = new StringBuilder();
        for (String line : normalized.split("\\R")) {
            if (isInternalLine(line)) {
                continue;
            }
            cleaned.append(line).append(System.lineSeparator());
        }
        String result = cleaned.toString().trim();
        return result.isBlank()
                ? "我在这里，可以继续告诉我你想完成的事情。"
                : result;
    }

    /**
     * 渲染致命执行失败的兜底用户消息。
     *
     * <p>此方法只负责用户可见降噪，不替代 Agent Path、日志和审计表中的内部
     * 错误记录。异常原文应留在内部观测链路中，避免 SQL、HTTP、堆栈或供应商错误
     * 直接污染 Conversation Context。</p>
     *
     * @param phase 失败发生的大致阶段
     * @param failure 原始异常
     * @return 用户可见失败说明
     */
    public String renderFailure(String phase, RuntimeException failure) {
        String normalizedPhase = phase == null || phase.isBlank()
                ? "任务执行"
                : phase.trim();
        return """
                %s时遇到了一个内部执行问题，我已经把失败节点和上下文记录在右侧执行链路中。

                你可以直接让我重试，或者补充更多约束后重新发起；我会基于已有上下文继续调整。
                """.formatted(normalizedPhase).trim();
    }

    /**
     * 过滤明显属于执行框架而非用户结果的行。
     */
    private boolean isInternalLine(String line) {
        String value = line == null ? "" : line.trim();
        return value.matches(".*(LoopNode|TaskRun|Control|Checkpoint|"
                + "Observation|Evidence|toolId|web\\.fetch|web\\.extract|"
                + "web\\.search|系统规则|内部执行|上下文构建|工具调用|节点已|"
                + "当前节点|执行闭环).*");
    }
}
