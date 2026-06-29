package com.funjson.metaagent.control.application;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.funjson.metaagent.clarification.application.ClarificationService;
import com.funjson.metaagent.conversation.application.AssistantMessageService;
import com.funjson.metaagent.conversation.application.UserFacingResponseRenderer;
import com.funjson.metaagent.job.application.JobExecutionCoordinator;
import com.funjson.metaagent.job.application.JobReplayService;
import com.funjson.metaagent.recovery.application.TaskRunResumeExecutor;
import com.funjson.metaagent.task.domain.TaskRunStatus;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 本机后台 Job Worker，用于把聊天轮次从同步执行中解耦。
 *
 * <p>v0.1 先使用进程内虚拟线程提交任务；后续可替换为 Outbox/队列驱动的
 * 持久化 Worker，而不改变 ControlKernel 的职责边界。</p>
 */
@Service
public class ControlJobWorker {

    private final JobExecutionCoordinator executionCoordinator;
    private final JobReplayService jobReplayService;
    private final TaskRunResumeExecutor resumeExecutor;
    private final AssistantMessageService assistantMessageService;
    private final ClarificationService clarificationService;
    private final UserFacingResponseRenderer responseRenderer;
    private final ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 创建 Control Job Worker。
     *
     * @param executionCoordinator Job 执行协调器
     * @param jobReplayService Job 重放查询服务
     * @param resumeExecutor TaskRun 恢复执行器
     * @param assistantMessageService Assistant 消息服务
     * @param clarificationService Clarification Service
     * @param responseRenderer 用户可见回复渲染器
     */
    public ControlJobWorker(
            JobExecutionCoordinator executionCoordinator,
            JobReplayService jobReplayService,
            TaskRunResumeExecutor resumeExecutor,
            AssistantMessageService assistantMessageService,
            ClarificationService clarificationService,
            UserFacingResponseRenderer responseRenderer) {
        this.executionCoordinator = executionCoordinator;
        this.jobReplayService = jobReplayService;
        this.resumeExecutor = resumeExecutor;
        this.assistantMessageService = assistantMessageService;
        this.clarificationService = clarificationService;
        this.responseRenderer = responseRenderer;
    }

    /**
     * 异步提交一个已创建的 Job。
     *
     * @param command Job 启动命令
     */
    public void submit(JobStartCommand command) {
        executor.submit(() -> run(command));
    }

    /**
     * 异步提交一个已经收到澄清回答的 TaskRun 恢复任务。
     *
     * @param command TaskRun 恢复命令
     */
    public void submitResume(TaskRunResumeCommand command) {
        executor.submit(() -> runResume(command));
    }

    /**
     * Replays Jobs that were created durably but never submitted to a worker.
     */
    @Scheduled(fixedDelayString = "${meta-agent.worker.job-replay-delay-ms:30000}")
    public void replayStartableJobs() {
        for (var candidate : jobReplayService.findStartableJobs(8)) {
            submit(new JobStartCommand(
                    candidate.conversationId(),
                    candidate.sourceMessageId(),
                    candidate.jobId(),
                    candidate.version(),
                    "job-replay:" + candidate.jobId()
                            + ":" + candidate.version()));
        }
    }

    /**
     * 在后台执行 Job，并把最终结果写回 Conversation。
     *
     * @param command Job 启动命令
     */
    private void run(JobStartCommand command) {
        try {
            var taskRun = executionCoordinator.startJob(
                    command.jobId(),
                    command.expectedVersion(),
                    command.idempotencyKey());
            if (taskRun.status() == TaskRunStatus.WAITING_HUMAN) {
                clarificationService.findOpenByJob(command.jobId())
                        .ifPresent(clarification -> assistantMessageService.append(
                                command.conversationId(),
                                command.userMessageId(),
                                command.jobId(),
                                taskRun.id(),
                                clarification.question(),
                                "CLARIFICATION_QUESTION"));
                return;
            }
            assistantMessageService.append(
                    command.conversationId(),
                    command.userMessageId(),
                    command.jobId(),
                    taskRun.id(),
                    responseRenderer.render(taskRun.resultSummary()),
                    "TASK_RESULT");
        } catch (RuntimeException failure) {
            assistantMessageService.append(
                    command.conversationId(),
                    command.userMessageId(),
                    command.jobId(),
                    null,
                    "任务执行失败：" + failure.getMessage(),
                    "TASK_FAILURE");
        }
    }

    /**
     * 后台恢复等待用户回答的 TaskRun。
     *
     * @param command TaskRun 恢复命令
     */
    private void runResume(TaskRunResumeCommand command) {
        try {
            var taskRun = resumeExecutor.resume(command.taskRunId());
            if (taskRun.status() == TaskRunStatus.WAITING_HUMAN) {
                clarificationService.findOpenByJob(command.jobId())
                        .ifPresent(clarification -> assistantMessageService.append(
                                command.conversationId(),
                                command.userMessageId(),
                                command.jobId(),
                                taskRun.id(),
                                clarification.question(),
                                "CLARIFICATION_QUESTION"));
                return;
            }
            assistantMessageService.append(
                    command.conversationId(),
                    command.userMessageId(),
                    command.jobId(),
                    taskRun.id(),
                    responseRenderer.render(taskRun.resultSummary()),
                    "TASK_RESULT");
        } catch (RuntimeException failure) {
            assistantMessageService.append(
                    command.conversationId(),
                    command.userMessageId(),
                    command.jobId(),
                    command.taskRunId(),
                    "澄清恢复失败：" + failure.getMessage(),
                    "TASK_FAILURE");
        }
    }

    /**
     * 关闭本机 Worker 线程池。
     */
    @PreDestroy
    public void close() {
        executor.close();
    }

    /**
     * Control 向后台 Worker 提交的启动命令。
     *
     * @param conversationId Conversation ID
     * @param userMessageId 来源用户消息 ID
     * @param jobId Job ID
     * @param expectedVersion Job 期望版本
     * @param idempotencyKey 启动幂等键
     */
    public record JobStartCommand(
            UUID conversationId,
            UUID userMessageId,
            UUID jobId,
            long expectedVersion,
            String idempotencyKey) {
    }

    /**
     * Control 向后台 Worker 提交的恢复命令。
     *
     * @param conversationId Conversation ID
     * @param userMessageId 来源用户消息 ID
     * @param jobId Job ID
     * @param taskRunId 待恢复 TaskRun ID
     */
    public record TaskRunResumeCommand(
            UUID conversationId,
            UUID userMessageId,
            UUID jobId,
            UUID taskRunId) {
    }
}
