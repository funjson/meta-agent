package com.funjson.metaagent.job.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.funjson.metaagent.task.domain.TaskStatus;

/**
 * 校验模型或规则产生的 TaskGraph 是否满足 Job 调度不变量。
 */
public class TaskGraphValidator {

    private static final int MAX_TASKS = 12;
    private static final String TASK_KEY_PATTERN = "[a-z][a-z0-9-]{0,49}";

    /**
     * 校验节点数量、Key、依赖引用、初始状态和 DAG 无环性。
     *
     * @param plan 待校验 Task Graph
     * @return 原规划，便于应用层组合调用
     */
    public TaskGraphPlan validate(TaskGraphPlan plan) {
        if (plan.nodes().isEmpty() || plan.nodes().size() > MAX_TASKS) {
            throw new IllegalArgumentException(
                    "Task graph must contain between 1 and "
                            + MAX_TASKS + " tasks");
        }

        Map<String, TaskGraphNodePlan> nodesByKey = new HashMap<>();
        for (TaskGraphNodePlan node : plan.nodes()) {
            validateNode(node);
            if (nodesByKey.put(node.key(), node) != null) {
                throw new IllegalArgumentException(
                        "Duplicate task key: " + node.key());
            }
        }
        for (TaskGraphNodePlan node : plan.nodes()) {
            validateDependencies(node, nodesByKey);
        }

        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (TaskGraphNodePlan node : plan.nodes()) {
            visit(node.key(), nodesByKey, visiting, visited);
        }
        return plan;
    }

    /**
     * 校验单个节点的字段和初始状态。
     *
     * @param node Task 节点
     */
    private void validateNode(TaskGraphNodePlan node) {
        if (node.key() == null
                || !node.key().matches(TASK_KEY_PATTERN)) {
            throw new IllegalArgumentException(
                    "Invalid task key: " + node.key());
        }
        if (node.title() == null || node.title().isBlank()
                || node.goal() == null || node.goal().isBlank()) {
            throw new IllegalArgumentException(
                    "Task title and goal must not be blank");
        }
        if (!"LOOP".equals(node.executionMode())) {
            throw new IllegalArgumentException(
                    "Unsupported execution mode: " + node.executionMode());
        }
        TaskStatus expectedStatus = node.dependsOnKeys().isEmpty()
                ? TaskStatus.READY
                : TaskStatus.BLOCKED;
        if (node.initialStatus() != TaskStatus.WAITING_HUMAN
                && node.initialStatus() != expectedStatus) {
            throw new IllegalArgumentException(
                    "Task initial status does not match dependencies: "
                            + node.key());
        }
    }

    /**
     * 校验依赖存在且不包含自身或重复引用。
     *
     * @param node Task 节点
     * @param nodesByKey 全图节点
     */
    private void validateDependencies(
            TaskGraphNodePlan node,
            Map<String, TaskGraphNodePlan> nodesByKey) {
        Set<String> uniqueDependencies = new HashSet<>();
        for (String dependency : node.dependsOnKeys()) {
            if (!nodesByKey.containsKey(dependency)) {
                throw new IllegalArgumentException(
                        "Unknown task dependency: " + dependency);
            }
            if (node.key().equals(dependency)) {
                throw new IllegalArgumentException(
                        "Task cannot depend on itself: " + node.key());
            }
            if (!uniqueDependencies.add(dependency)) {
                throw new IllegalArgumentException(
                        "Duplicate task dependency: " + dependency);
            }
        }
    }

    /**
     * 深度优先检查依赖图是否存在环。
     *
     * @param key 当前节点 Key
     * @param nodesByKey 全图节点
     * @param visiting 当前递归栈
     * @param visited 已完成节点
     */
    private void visit(
            String key,
            Map<String, TaskGraphNodePlan> nodesByKey,
            Set<String> visiting,
            Set<String> visited) {
        if (visited.contains(key)) {
            return;
        }
        if (!visiting.add(key)) {
            throw new IllegalArgumentException(
                    "Task graph contains a cycle at: " + key);
        }
        for (String dependency
                : nodesByKey.get(key).dependsOnKeys()) {
            visit(dependency, nodesByKey, visiting, visited);
        }
        visiting.remove(key);
        visited.add(key);
    }
}
