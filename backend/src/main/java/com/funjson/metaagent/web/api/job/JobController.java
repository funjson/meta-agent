package com.funjson.metaagent.web.api.job;

import java.net.URI;
import java.util.UUID;

import com.funjson.metaagent.job.api.CreateJobRequest;
import com.funjson.metaagent.job.api.JobPage;
import com.funjson.metaagent.job.api.JobView;
import com.funjson.metaagent.job.application.JobExecutionCoordinator;
import com.funjson.metaagent.job.application.JobService;
import com.funjson.metaagent.task.api.StartJobRequest;
import com.funjson.metaagent.task.api.TaskRunView;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供 Job 的兼容管理接口。
 *
 * <p>产品主入口是 Conversation API；该接口保留给诊断、兼容和后续管理页面。</p>
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;
    private final JobExecutionCoordinator executionCoordinator;

    /**
     * 创建 Job Controller。
     *
     * @param jobService Job Application Service
     * @param executionCoordinator Job 执行协调器
     */
    public JobController(
            JobService jobService,
            JobExecutionCoordinator executionCoordinator) {
        this.jobService = jobService;
        this.executionCoordinator = executionCoordinator;
    }

    /**
     * 通过兼容入口创建 Job。
     *
     * @param idempotencyKey 幂等键
     * @param request 创建请求
     * @return 新 Job
     */
    @PostMapping
    public ResponseEntity<JobView> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateJobRequest request) {
        JobView created = jobService.create(idempotencyKey, request);
        return ResponseEntity
                .created(URI.create("/api/v1/jobs/" + created.id()))
                .body(created);
    }

    /**
     * 分页查询 Job。
     *
     * @param page 页码
     * @param size 页大小
     * @return Job 分页
     */
    @GetMapping
    public JobPage list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return jobService.list(page, size);
    }

    /**
     * 查询 Job。
     *
     * @param jobId Job ID
     * @return Job
     */
    @GetMapping("/{jobId}")
    public JobView get(@PathVariable UUID jobId) {
        return jobService.get(jobId);
    }

    /**
     * 通过兼容入口启动 Job。
     *
     * @param jobId Job ID
     * @param idempotencyKey 幂等键
     * @param request 启动请求
     * @return TaskRun
     */
    @PostMapping("/{jobId}/start")
    public ResponseEntity<TaskRunView> start(
            @PathVariable UUID jobId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StartJobRequest request) {
        TaskRunView run = executionCoordinator.startJob(
                jobId,
                request.expectedVersion(),
                idempotencyKey);
        return ResponseEntity.accepted().body(run);
    }
}
