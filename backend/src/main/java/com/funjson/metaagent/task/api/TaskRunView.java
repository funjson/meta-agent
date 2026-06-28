package com.funjson.metaagent.task.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.loop.api.CheckpointView;
import com.funjson.metaagent.loop.api.EvidenceView;
import com.funjson.metaagent.loop.api.LoopNodeView;
import com.funjson.metaagent.task.domain.TaskRunStatus;

/**
 * TaskRun 及其 Loop、Checkpoint 和 Evidence 的 API 视图。
 *
 * @param id TaskRun ID
 * @param taskId Task ID
 * @param runType 运行类型
 * @param status 状态
 * @param attemptNo 尝试序号
 * @param resultSummary 结果摘要
 * @param startedAt 开始时间
 * @param completedAt 完成时间
 * @param loopNodes LoopNode 列表
 * @param checkpoints Checkpoint 列表
 * @param evidence Evidence 列表
 */
public record TaskRunView(
        UUID id,
        UUID taskId,
        String runType,
        TaskRunStatus status,
        int attemptNo,
        String resultSummary,
        Instant startedAt,
        Instant completedAt,
        List<LoopNodeView> loopNodes,
        List<CheckpointView> checkpoints,
        List<EvidenceView> evidence) {
}
