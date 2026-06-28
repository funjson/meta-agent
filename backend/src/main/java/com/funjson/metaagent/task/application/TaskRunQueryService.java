package com.funjson.metaagent.task.application;

import com.funjson.metaagent.loop.api.CheckpointView;
import com.funjson.metaagent.task.api.TaskRunView;
import com.funjson.metaagent.task.application.port.out.TaskRunQueryStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 提供 TaskRun 与 Checkpoint 的只读查询用例。
 */
@Service
public class TaskRunQueryService {

    private final TaskRunQueryStore store;

    /**
     * 创建 TaskRun 查询服务。
     *
     * @param store 查询持久化端口
     */
    public TaskRunQueryService(TaskRunQueryStore store) {
        this.store = store;
    }

    /**
     * 查询 TaskRun。
     *
     * @param taskRunId TaskRun ID
     * @return TaskRun
     */
    @Transactional(readOnly = true)
    public TaskRunView get(UUID taskRunId) {
        return store.findTaskRun(taskRunId);
    }

    /**
     * 查询 TaskRun Checkpoint。
     *
     * @param taskRunId TaskRun ID
     * @return Checkpoint
     */
    @Transactional(readOnly = true)
    public List<CheckpointView> checkpoints(UUID taskRunId) {
        return store.findCheckpoints(taskRunId);
    }
}
