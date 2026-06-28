package com.funjson.metaagent.loop.domain;

/**
 * 携带实际失败 LoopNode 上下文的执行异常。
 */
public class LoopExecutionException extends RuntimeException {

    private final RunExecutionContext context;
    private final RuntimeException originalFailure;

    /**
     * 创建执行异常。
     *
     * @param context 实际失败节点上下文
     * @param originalFailure 原始异常
     */
    public LoopExecutionException(
            RunExecutionContext context,
            RuntimeException originalFailure) {
        super(originalFailure.getMessage(), originalFailure);
        this.context = context;
        this.originalFailure = originalFailure;
    }

    /**
     * 返回实际失败节点上下文。
     *
     * @return 节点上下文
     */
    public RunExecutionContext context() {
        return context;
    }

    /**
     * 返回原始异常。
     *
     * @return 原始异常
     */
    public RuntimeException originalFailure() {
        return originalFailure;
    }
}
