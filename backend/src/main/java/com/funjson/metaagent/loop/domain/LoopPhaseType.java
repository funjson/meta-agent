package com.funjson.metaagent.loop.domain;

/**
 * LoopNode 内部可审计阶段。
 *
 * <p>阶段属于 LoopNode，不是新的核心执行对象，也不能替代 Child LoopNode。</p>
 */
public enum LoopPhaseType {

    CONTEXT_BUILD(1),
    PLANNING(2),
    ACTION_PREPARATION(3),
    ACTION_EXECUTION(4),
    OBSERVATION(5),
    EVALUATION(6),
    ADJUSTMENT(7);

    private final int sequence;

    /**
     * 创建阶段定义。
     *
     * @param sequence 节点内稳定顺序
     */
    LoopPhaseType(int sequence) {
        this.sequence = sequence;
    }

    /**
     * 返回节点内稳定顺序。
     *
     * @return 顺序号
     */
    public int sequence() {
        return sequence;
    }
}
