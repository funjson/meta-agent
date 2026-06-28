package com.funjson.metaagent.job.domain;

import java.util.List;

import com.funjson.metaagent.task.domain.TaskStatus;

/**
 * 描述 Job 初始化阶段形成的一个 Task 节点。
 *
 * @param key Job 内稳定且唯一的 Task Key
 * @param title 面向用户和路径投影的标题
 * @param goal 可独立执行的 Task 目标
 * @param initialStatus 初始调度状态
 * @param executionMode TaskRun 入口模式
 * @param dependsOnKeys 前置 Task Key
 */
public record TaskGraphNodePlan(
        String key,
        String title,
        String goal,
        TaskStatus initialStatus,
        String executionMode,
        List<String> dependsOnKeys) {

    /**
     * 复制可变集合，保证规划结果在持久化期间保持稳定。
     */
    public TaskGraphNodePlan {
        dependsOnKeys = List.copyOf(dependsOnKeys);
    }
}
