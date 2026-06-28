package com.funjson.metaagent.loop.domain;

import java.util.UUID;

import com.funjson.metaagent.capability.domain.CapabilityRef;

/**
 * Loop Kernel 执行一次 TaskRun 所需的稳定上下文。
 *
 * @param jobId Job ID
 * @param taskId Task ID
 * @param taskRunId TaskRun ID
 * @param loopRunId LoopRun ID
 * @param loopNodeId 当前 LoopNode ID
 * @param parentNodeId 父 LoopNode ID，根节点为空
 * @param depth 节点深度
 * @param iterationNo 当前线性调整序号
 * @param loopRunParentType LoopRun 的直接父运行对象类型
 * @param loopRunParentId LoopRun 的直接父运行对象 ID
 * @param recursionDepth 当前 Job 树递归深度快照
 * @param providerId Provider ID
 * @param goal 当前目标
 * @param feedback 父节点 Evaluation 传入的调整反馈
 * @param pendingCapability 待由当前节点加载的显式 Capability
 */
public record RunExecutionContext(
        UUID jobId,
        UUID taskId,
        UUID taskRunId,
        UUID loopRunId,
        UUID loopNodeId,
        UUID parentNodeId,
        int depth,
        int iterationNo,
        LoopRunParentType loopRunParentType,
        UUID loopRunParentId,
        int recursionDepth,
        String providerId,
        String goal,
        String feedback,
        CapabilityRef pendingCapability) {
}
