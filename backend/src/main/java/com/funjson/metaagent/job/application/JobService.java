package com.funjson.metaagent.job.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.job.api.CreateJobRequest;
import com.funjson.metaagent.job.api.JobPage;
import com.funjson.metaagent.job.api.JobView;
import com.funjson.metaagent.job.application.port.out.JobStore;
import com.funjson.metaagent.job.domain.JobCreationContext;
import com.funjson.metaagent.job.domain.JobNotFoundException;
import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.job.domain.TaskGraphPlan;
import com.funjson.metaagent.provider.application.ModelCatalogService;
import com.funjson.metaagent.task.domain.TaskStatus;

/**
 * 负责 Job 与初始 TaskGraph 的创建用例。
 */
@Service
public class JobService {

    private static final String CREATE_JOB_COMMAND = "CREATE_JOB";

    private final JobStore jobStore;
    private final ObjectMapper objectMapper;
    private final ModelCatalogService modelCatalog;

    /**
     * 创建 Job Application Service。
     *
     * @param jobStore Job Store Port
     * @param objectMapper JSON 序列化器
     */
    public JobService(
            JobStore jobStore,
            ObjectMapper objectMapper,
            ModelCatalogService modelCatalog) {
        this.jobStore = jobStore;
        this.objectMapper = objectMapper;
        this.modelCatalog = modelCatalog;
    }

    /**
     * 通过兼容入口创建 Job。
     *
     * @param idempotencyKey 幂等键
     * @param request 创建请求
     * @return Job
     */
    @Transactional
    public JobView create(String idempotencyKey, CreateJobRequest request) {
        return create(
                idempotencyKey,
                request,
                JobCreationContext.standalone(),
                TaskGraphPlan.single(request.originalRequest().trim()));
    }

    /**
     * 使用显式来源上下文创建 Job。
     *
     * @param idempotencyKey 幂等键
     * @param request 创建请求
     * @param context 来源上下文
     * @return Job
     */
    @Transactional
    public JobView create(
            String idempotencyKey,
            CreateJobRequest request,
            JobCreationContext context) {
        return create(
                idempotencyKey,
                request,
                context,
                TaskGraphPlan.single(request.originalRequest().trim()));
    }

    /**
     * 使用显式来源上下文和已验证 Task Graph 创建 Job。
     *
     * @param idempotencyKey 幂等键
     * @param request 创建请求
     * @param context 来源上下文
     * @param taskGraph 已验证 Task Graph
     * @return Job
     */
    @Transactional
    public JobView create(
            String idempotencyKey,
            CreateJobRequest request,
            JobCreationContext context,
            TaskGraphPlan taskGraph) {
        return jobStore.findResourceIdByIdempotencyKey(idempotencyKey, CREATE_JOB_COMMAND)
                .map(this::get)
                .orElseGet(() -> createNew(
                        idempotencyKey,
                        request,
                        context,
                        taskGraph));
    }

    /**
     * 查询 Job 及其 Task。
     *
     * @param id Job ID
     * @return Job
     */
    @Transactional(readOnly = true)
    public JobView get(UUID id) {
        JobView job = jobStore.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));
        List<JobView> children = jobStore.findChildren(id).stream()
                .map(child -> child.withTasks(
                        jobStore.findTasksByJobId(child.id())))
                .toList();
        return job.withTasks(jobStore.findTasksByJobId(id))
                .withChildJobs(children);
    }

    /**
     * 分页查询 Job。
     *
     * @param page 页码
     * @param size 页大小
     * @return Job 分页
     */
    @Transactional(readOnly = true)
    public JobPage list(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        List<JobView> items = jobStore.findAll(safeSize, safePage * safeSize).stream()
                .map(job -> job.withTasks(jobStore.findTasksByJobId(job.id())))
                .toList();
        return new JobPage(items, safePage, safeSize, jobStore.countAll());
    }

    /**
     * 用户回答澄清问题后恢复原 TaskGraph 等待节点。
     *
     * @param jobId Job ID
     * @param taskId Task ID
     * @param answer 用户回答
     * @return 恢复后的 Job
     */
    @Transactional
    public JobView resumeAfterClarification(
            UUID jobId,
            UUID taskId,
            String answer) {
        return resumeAfterClarification(
                jobId,
                taskId,
                answer,
                "{}",
                "");
    }

    /**
     * 用户回答澄清问题后恢复原 TaskGraph 等待节点，并写入结构化补充事实。
     *
     * @param jobId Job ID
     * @param taskId Task ID
     * @param answer 用户回答
     * @param extractedFactsJson 抽取事实 JSON
     * @param answerSummary 系统审计摘要
     * @return 恢复后的 Job
     */
    @Transactional
    public JobView resumeAfterClarification(
            UUID jobId,
            UUID taskId,
            String answer,
            String extractedFactsJson,
            String answerSummary) {
        jobStore.resumeTaskAfterClarification(
                jobId,
                taskId,
                answer,
                extractedFactsJson,
                answerSummary);
        return get(jobId);
    }

    /**
     * 在一个 Job 创建事务中写入 Job、TaskGraph、事件和幂等记录。
     *
     * @param idempotencyKey 幂等键
     * @param request 创建请求
     * @param context 来源上下文
     * @param taskGraph 已验证 Task Graph
     * @return 新 Job
     */
    private JobView createNew(
            String idempotencyKey,
            CreateJobRequest request,
            JobCreationContext context,
            TaskGraphPlan taskGraph) {
        UUID jobId = UUID.randomUUID();
        String normalizedRequest = request.originalRequest().trim();
        String providerId = request.providerId() == null || request.providerId().isBlank()
                ? "fake"
                : request.providerId().trim();
        validateExecutorModel(providerId);
        String goalSummary = summarize(normalizedRequest);
        JobStatus initialJobStatus = taskGraph.nodes().stream()
                .anyMatch(node -> node.initialStatus() == TaskStatus.READY)
                ? JobStatus.CREATED
                : JobStatus.WAITING_HUMAN;
        Map<String, UUID> taskIds = new LinkedHashMap<>();
        taskGraph.nodes().forEach(
                node -> taskIds.put(node.key(), UUID.randomUUID()));
        String eventPayload = toJson(new JobCreatedPayload(
                jobId,
                taskGraph.source(),
                taskGraph.summary(),
                taskGraph.nodes().stream()
                        .map(node -> new TaskReference(
                                taskIds.get(node.key()),
                                node.key(),
                                node.initialStatus().name(),
                                node.dependsOnKeys()))
                        .toList(),
                initialJobStatus.name(),
                taskGraph.nodes().size()));

        // Job、完整 Task DAG、Event、Outbox 和幂等记录必须共同提交。
        jobStore.insertJob(
                jobId,
                normalizedRequest,
                goalSummary,
                providerId,
                initialJobStatus,
                context);
        for (int index = 0; index < taskGraph.nodes().size(); index++) {
            var node = taskGraph.nodes().get(index);
            jobStore.insertTask(
                    taskIds.get(node.key()),
                    jobId,
                    node.key(),
                    index + 1,
                    node.title(),
                    node.goal(),
                    node.initialStatus(),
                    node.executionMode());
        }
        for (var node : taskGraph.nodes()) {
            for (String dependencyKey : node.dependsOnKeys()) {
                jobStore.insertTaskDependency(
                        taskIds.get(node.key()),
                        taskIds.get(dependencyKey));
            }
        }
        UUID firstTaskId = taskIds.get(
                taskGraph.nodes().getFirst().key());
        jobStore.insertRuntimeEvent(
                UUID.randomUUID(),
                jobId,
                firstTaskId,
                "JOB_CREATED",
                eventPayload);
        jobStore.insertOutboxEvent(
                UUID.randomUUID(),
                "JOB_CREATED",
                eventPayload);
        jobStore.registerIdempotencyKey(idempotencyKey, CREATE_JOB_COMMAND, jobId);

        return get(jobId);
    }

    /**
     * 生成适合列表展示的单行目标摘要。
     *
     * @param request 原始请求
     * @return 摘要
     */
    private String summarize(String request) {
        String singleLine = request.replaceAll("\\s+", " ");
        return singleLine.length() <= 120
                ? singleLine
                : singleLine.substring(0, 117) + "...";
    }

    /**
     * Validates that the persisted executor reference is either a framework
     * model ID or a legacy provider ID.
     *
     * <p>New Control flows persist the framework model ID, for example
     * {@code deepseek-v4-pro}. Older internal callers may still pass provider
     * IDs such as {@code deepseek}; those remain valid until every API has been
     * renamed from providerId to executorModelId.</p>
     *
     * @param executorRef model ID or legacy provider ID
     */
    private void validateExecutorModel(String executorRef) {
        // Model IDs are the formal path: Job stores the user's chosen executor
        // while provider adapters remain hidden behind the model catalog.
        if (modelCatalog.find(executorRef).isPresent()) {
            return;
        }
        // Legacy provider IDs are kept for old conversations, tests and direct
        // API calls that predate the model catalog.
        if ("deepseek".equals(executorRef) || "glm".equals(executorRef)) {
            return;
        }
        throw new IllegalArgumentException("Unsupported executor model: " + executorRef);
    }

    /**
     * 将事件负载序列化为 JSON。
     *
     * @param payload 负载
     * @return JSON
     */
    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize runtime event", exception);
        }
    }

    /**
     * JOB_CREATED 事件负载。
     *
     * @param jobId Job ID
     * @param taskGraphSource Task Graph 来源
     * @param taskGraphSummary Task Graph 摘要
     * @param tasks Task 引用
     * @param jobStatus Job 状态
     * @param taskCount Task 数量
     */
    private record JobCreatedPayload(
            UUID jobId,
            String taskGraphSource,
            String taskGraphSummary,
            List<TaskReference> tasks,
            String jobStatus,
            int taskCount) {
    }

    /**
     * JOB_CREATED 事件中的 Task Graph 节点引用。
     *
     * @param taskId Task ID
     * @param taskKey Task Key
     * @param status 初始状态
     * @param dependsOnKeys 前置 Task Key
     */
    private record TaskReference(
            UUID taskId,
            String taskKey,
            String status,
            List<String> dependsOnKeys) {
    }
}
