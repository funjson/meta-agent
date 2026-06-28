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
     * 过滤明显属于执行框架而非用户结果的行。
     */
    private boolean isInternalLine(String line) {
        String value = line == null ? "" : line.trim();
        return value.matches(".*(LoopNode|TaskRun|Control|Checkpoint|Observation|Evidence|上下文构建|工具调用|节点已|当前节点|执行闭环).*");
    }
}
