package com.funjson.metaagent.web.api.recovery;

import java.util.UUID;

import com.funjson.metaagent.recovery.api.RecoveryPlanView;
import com.funjson.metaagent.recovery.application.TaskRunRecoveryService;
import com.funjson.metaagent.recovery.application.TaskRunResumeExecutor;
import com.funjson.metaagent.task.api.TaskRunView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供 TaskRun 恢复检查和恢复准备 API。
 */
@RestController
@RequestMapping("/api/v1/task-runs/{taskRunId}/recovery")
public class TaskRunRecoveryController {

    private final TaskRunRecoveryService recoveryService;
    private final TaskRunResumeExecutor resumeExecutor;

    /**
     * 创建 TaskRun Recovery Controller。
     *
     * @param recoveryService Recovery Service
     * @param resumeExecutor Resume Executor
     */
    public TaskRunRecoveryController(
            TaskRunRecoveryService recoveryService,
            TaskRunResumeExecutor resumeExecutor) {
        this.recoveryService = recoveryService;
        this.resumeExecutor = resumeExecutor;
    }

    /**
     * 检查当前恢复边界。
     *
     * @param taskRunId TaskRun ID
     * @return 恢复计划
     */
    @GetMapping
    public RecoveryPlanView inspect(@PathVariable UUID taskRunId) {
        return recoveryService.inspect(taskRunId);
    }

    /**
     * 创建恢复准备记录。
     *
     * @param taskRunId TaskRun ID
     * @return 恢复计划
     */
    @PostMapping("/attempts")
    public RecoveryPlanView prepare(@PathVariable UUID taskRunId) {
        return recoveryService.prepare(taskRunId);
    }

    /**
     * 从最新安全 Checkpoint 真正续跑 TaskRun。
     *
     * @param taskRunId TaskRun ID
     * @return 完成后的 TaskRun
     */
    @PostMapping("/resume")
    public TaskRunView resume(@PathVariable UUID taskRunId) {
        return resumeExecutor.resume(taskRunId);
    }
}
