package com.funjson.metaagent.context.domain;

import java.util.List;
import java.util.UUID;

/**
 * 一次 LoopNode 执行前构建出的结构化上下文快照。
 *
 * @param taskRunId TaskRun ID
 * @param loopNodeId LoopNode ID
 * @param blocks 上下文块
 * @param tokenBudget Token 预算
 */
public record LoopContextSnapshot(
        UUID taskRunId,
        UUID loopNodeId,
        List<ContextBlock> blocks,
        int tokenBudget) {

    /**
     * 复制上下文块集合。
     */
    public LoopContextSnapshot {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    /**
     * 生成可放入 Prompt 的上下文摘要。
     *
     * @return Prompt 上下文
     */
    public String toPromptSummary() {
        return blocks.stream()
                .map(block -> "## %s · %s\n%s".formatted(
                        block.type(),
                        block.title(),
                        block.content()))
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("无上下文");
    }
}
