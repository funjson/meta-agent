package com.funjson.metaagent.task.domain;

import java.util.UUID;


/**
 * Job 调度事务内锁定的 Task 领域快照。
 *
 * @param id Task ID
 * @param goal Task 目标
 * @param dependencyContext 已完成前置 Task 的结果摘要
 * @param status Task 状态
 * @param version 乐观锁版本
 */
public record LockedTaskSnapshot(
        UUID id,
        String goal,
        String dependencyContext,
        TaskStatus status,
        long version) {
}
