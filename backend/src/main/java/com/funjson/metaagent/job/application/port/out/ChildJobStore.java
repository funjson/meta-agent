package com.funjson.metaagent.job.application.port.out;

import com.funjson.metaagent.job.domain.ChildJobParentSnapshot;
import com.funjson.metaagent.job.domain.ChildJobCompletionSnapshot;
import com.funjson.metaagent.runtime.domain.ChildJobRequest;

import java.util.Optional;
import java.util.UUID;

/**
 * JobCoordinator 物化阻塞型 Child Job 的持久化端口。
 */
public interface ChildJobStore {

    /** @return 幂等请求已经创建的 Child Job */
    Optional<UUID> findChildJobId(String idempotencyKey);

    /** @return 加锁父 Job 快照 */
    ChildJobParentSnapshot lockParent(UUID parentJobId);

    /** @return 父 Job 直接子 Job 数量 */
    long countDirectChildren(UUID parentJobId);

    /** @return 整棵 Job 树数量 */
    long countTreeJobs(UUID rootJobId);

    /** 插入父子派生事实。 */
    void insertDerivation(
            UUID derivationId,
            ChildJobParentSnapshot parent,
            UUID childJobId,
            UUID originTaskRunId,
            UUID originLoopNodeId,
            ChildJobRequest request,
            String requestJson);

    /** 把 origin LoopNode 绑定到活动 Child Job。 */
    void bindOriginLoopNode(
            UUID originLoopNodeId,
            UUID childJobId);

    /** @return 锁定的 Child Job 完成快照 */
    Optional<ChildJobCompletionSnapshot> lockCompletion(
            UUID childJobId);

    /** @return Child Job 结果摘要 */
    String summarizeChildJob(UUID childJobId);

    /** @return Child Job Evidence 数量 */
    int countChildJobEvidence(UUID childJobId);

    /** 幂等写入 ChildJobOutcome。 */
    boolean completeDerivation(
            UUID childJobId,
            String outcomeJson);

    /** 清除 origin LoopNode 的活动 Child Job 引用。 */
    void clearOriginLoopNode(
            UUID originLoopNodeId,
            UUID childJobId);
}
