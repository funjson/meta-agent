package com.funjson.metaagent.web.api.task;

import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.loop.api.CheckpointView;
import com.funjson.metaagent.task.api.TaskRunView;
import com.funjson.metaagent.task.application.TaskRunQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供 TaskRun、LoopNode、Checkpoint 和 Evidence 查询接口。
 */
@RestController
@RequestMapping("/api/v1/task-runs")
public class TaskRunController {

    private final TaskRunQueryService queryService;

    /**
     * 创建 TaskRun Controller。
     *
     * @param queryService TaskRun 查询服务
     */
    public TaskRunController(TaskRunQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 查询 TaskRun。
     *
     * @param taskRunId TaskRun ID
     * @return TaskRun
     */
    @GetMapping("/{taskRunId}")
    public TaskRunView get(@PathVariable UUID taskRunId) {
        return queryService.get(taskRunId);
    }

    /**
     * 查询 TaskRun 的 Checkpoint。
     *
     * @param taskRunId TaskRun ID
     * @return Checkpoint 列表
     */
    @GetMapping("/{taskRunId}/checkpoints")
    public List<CheckpointView> checkpoints(@PathVariable UUID taskRunId) {
        return queryService.checkpoints(taskRunId);
    }
}
