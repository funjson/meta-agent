package com.funjson.metaagent.task.api;

import java.util.UUID;
import java.util.List;

import com.funjson.metaagent.task.domain.TaskStatus;

/**
 * Task 与最近一次 TaskRun 的 API 视图。
 *
 * @param id Task ID
 * @param taskKey Job 内稳定 Key
 * @param sequenceNo Task Graph 顺序
 * @param title 标题
 * @param goal 目标
 * @param taskType Task 类型
 * @param status 状态
 * @param executionMode 执行模式
 * @param latestTaskRunId 最近运行 ID
 * @param latestTaskRunStatus 最近运行状态
 * @param resultSummary 结果摘要
 * @param dependsOnTaskKeys 前置 Task Key
 * @param version 版本
 */
public record TaskView(
        UUID id,
        String taskKey,
        int sequenceNo,
        String title,
        String goal,
        String taskType,
        TaskStatus status,
        String executionMode,
        UUID latestTaskRunId,
        String latestTaskRunStatus,
        String resultSummary,
        List<String> dependsOnTaskKeys,
        long version) {
}
