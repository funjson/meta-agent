package com.funjson.metaagent.loop.application;

import com.funjson.metaagent.loop.domain.LoopOutcome;
import com.funjson.metaagent.loop.domain.RunExecutionContext;

/**
 * 执行一个已经物化完成的 LoopRun。
 *
 * <p>上层只提交稳定运行上下文；Loop Kernel 不读取或修改 Job/Task 聚合。</p>
 */
public interface LoopRunExecutor {

    /**
     * 执行 LoopRun 及其 LoopTree。
     *
     * @param rootContext LoopRun 根节点上下文
     * @return Loop 执行结果
     */
    LoopOutcome execute(RunExecutionContext rootContext);
}
