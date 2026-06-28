package com.funjson.metaagent.task.application.port.out;

import com.funjson.metaagent.loop.api.CheckpointView;
import com.funjson.metaagent.task.api.TaskRunView;

import java.util.List;
import java.util.UUID;

/**
 * Task 模块查询 TaskRun 聚合视图的持久化端口。
 */
public interface TaskRunQueryStore {

    /** @return TaskRun 详情 */
    TaskRunView findTaskRun(UUID taskRunId);

    /** @return TaskRun Checkpoint */
    List<CheckpointView> findCheckpoints(UUID taskRunId);
}
