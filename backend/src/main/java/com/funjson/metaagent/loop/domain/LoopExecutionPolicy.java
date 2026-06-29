package com.funjson.metaagent.loop.domain;

/**
 * C3 最小 LoopTree 的执行边界。
 *
 * @param maxDepth 最大 Child 深度，根节点深度为 0
 * @param maxLoopNodes 单个 LoopRun 最大节点数
 */
public record LoopExecutionPolicy(
        int maxDepth,
        int maxLoopNodes) {

    /**
     * 返回当前正式基线使用的保守策略。
     *
     * @return 默认策略
     */
    public static LoopExecutionPolicy baseline() {
        return new LoopExecutionPolicy(200, 300);
    }

    /**
     * 校验策略参数。
     */
    public LoopExecutionPolicy {
        if (maxDepth < 0 || maxLoopNodes < 1) {
            throw new IllegalArgumentException(
                    "Loop policy limits must be positive");
        }
    }
}
