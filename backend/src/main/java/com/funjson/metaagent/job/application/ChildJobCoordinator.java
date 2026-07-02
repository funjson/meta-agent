package com.funjson.metaagent.job.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.job.api.CreateJobRequest;
import com.funjson.metaagent.job.api.JobView;
import com.funjson.metaagent.job.api.TaskGraphTemplateView;
import com.funjson.metaagent.job.application.port.out.ChildJobStore;
import com.funjson.metaagent.job.domain.JobCreationContext;
import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.job.domain.TaskGraphPlan;
import com.funjson.metaagent.job.domain.TaskGraphPlanningRequest;
import com.funjson.metaagent.loop.application.port.out.RuntimeStore;
import com.funjson.metaagent.runtime.domain.ChildJobRequest;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.profile.application.SubagentProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * 负责把 LoopNode 的中立 ChildJobRequest 原子物化为子 Job。
 */
@Service
public class ChildJobCoordinator {

    private static final int MAX_DEPTH = 3;
    private static final int MAX_DIRECT_CHILDREN = 8;
    private static final int MAX_TREE_JOBS = 32;

    private final ChildJobStore store;
    private final JobService jobService;
    private final TaskGraphTemplateService templateService;
    private final TaskGraphPlanner taskGraphPlanner;
    private final RuntimeStore runtimeStore;
    private final SubagentProfileService subagentProfiles;
    private final ObjectMapper objectMapper;

    /**
     * 创建 Child Job 协调器。
     *
     * @param store Child Job 持久化端口
     * @param jobService Job 创建服务
     * @param templateService 模板服务
     * @param taskGraphPlanner 动态 TaskGraph Planner
     * @param runtimeStore Runtime 事件与 Checkpoint 端口
     * @param subagentProfiles SubagentProfile Service
     * @param objectMapper JSON 序列化器
     */
    public ChildJobCoordinator(
            ChildJobStore store,
            JobService jobService,
            TaskGraphTemplateService templateService,
            TaskGraphPlanner taskGraphPlanner,
            RuntimeStore runtimeStore,
            SubagentProfileService subagentProfiles,
            ObjectMapper objectMapper) {
        this.store = store;
        this.jobService = jobService;
        this.templateService = templateService;
        this.taskGraphPlanner = taskGraphPlanner;
        this.runtimeStore = runtimeStore;
        this.subagentProfiles = subagentProfiles;
        this.objectMapper = objectMapper;
    }

    /**
     * 幂等创建阻塞型 Child Job，并记录父恢复安全点。
     *
     * @param parentJobId 父 Job ID
     * @param originTaskRunId origin TaskRun ID
     * @param originLoopRunId origin LoopRun ID
     * @param originLoopNodeId origin LoopNode ID
     * @param request Child Job 请求
     * @return Child Job
     */
    @Transactional
    public JobView materialize(
            UUID parentJobId,
            UUID originTaskRunId,
            UUID originLoopRunId,
            UUID originLoopNodeId,
            ChildJobRequest request) {
        var existing = store.findChildJobId(request.idempotencyKey());
        if (existing.isPresent()) {
            return jobService.get(existing.get());
        }

        var parent = store.lockParent(parentJobId);
        requireWithinLimits(parent);
        TaskGraphTemplateView template = resolveTemplate(
                parent.agentProfileId(),
                request);
        TaskGraphPlan graph = template == null
                ? dynamicPlan(request)
                : template.graph();
        if (request.subagentProfileRef() != null) {
            subagentProfiles.requireCompatible(
                    parent.agentProfileId(),
                    request.subagentProfileRef());
        }
        JobCreationContext context = new JobCreationContext(
                parent.agentProfileId(),
                parent.conversationId(),
                parent.sourceMessageId(),
                parent.jobId(),
                parent.rootJobId(),
                parent.recursionDepth() + 1,
                template == null ? null : template.id(),
                template == null ? null : template.version(),
                request.subagentProfileRef() == null
                        ? null
                        : request.subagentProfileRef().id(),
                request.subagentProfileRef() == null
                        ? null
                        : request.subagentProfileRef().version(),
                "{\"version\":\"v1\",\"origin\":\"child-job\"}");
        JobView child = jobService.create(
                "child-job:" + request.idempotencyKey(),
                new CreateJobRequest(
                        request.goal(),
                        parent.providerId()),
                context,
                graph);

        // 子 Job、派生关系和 origin 绑定共享当前事务，崩溃重试由幂等键收敛。
        store.insertDerivation(
                UUID.randomUUID(),
                parent,
                child.id(),
                originTaskRunId,
                originLoopNodeId,
                request,
                json(request));
        store.bindOriginLoopNode(originLoopNodeId, child.id());
        String payload = json(Map.of(
                "parentJobId", parent.jobId(),
                "childJobId", child.id(),
                "rootJobId", parent.rootJobId(),
                "recursionDepth", child.recursionDepth(),
                "originTaskRunId", originTaskRunId,
                "originLoopNodeId", originLoopNodeId,
                "idempotencyKey", request.idempotencyKey()));
        long eventOffset = runtimeStore.insertRuntimeEvent(
                UUID.randomUUID(),
                parent.jobId(),
                null,
                originTaskRunId,
                "JOB_DERIVATION",
                child.id(),
                "CHILD_JOB_CREATED",
                payload);
        UUID checkpointId = UUID.randomUUID();
        runtimeStore.insertCheckpoint(
                checkpointId,
                originTaskRunId,
                originLoopRunId,
                originLoopNodeId,
                runtimeStore.nextCheckpointSequence(originTaskRunId),
                "CHILD_JOB_CREATED",
                payload,
                eventOffset);
        runtimeStore.updateLatestCheckpoint(
                originTaskRunId,
                checkpointId);
        return child;
    }

    /** 校验阻塞型 Child Job 递归边界。 */
    private void requireWithinLimits(
            com.funjson.metaagent.job.domain.ChildJobParentSnapshot parent) {
        if (parent.status() != JobStatus.RUNNING) {
            throw new RuntimeStateException(
                    "PARENT_JOB_NOT_RUNNING",
                    "Child Job can only derive from a running parent");
        }
        if (parent.recursionDepth() >= MAX_DEPTH) {
            throw new RuntimeStateException(
                    "CHILD_JOB_DEPTH_LIMIT",
                    "Child Job recursion depth exceeded");
        }
        if (store.countDirectChildren(parent.jobId())
                >= MAX_DIRECT_CHILDREN) {
            throw new RuntimeStateException(
                    "CHILD_JOB_DIRECT_LIMIT",
                    "Direct Child Job limit exceeded");
        }
        if (store.countTreeJobs(parent.rootJobId())
                >= MAX_TREE_JOBS) {
            throw new RuntimeStateException(
                    "CHILD_JOB_TREE_LIMIT",
                    "Job tree size limit exceeded");
        }
    }

    /** 解析显式模板引用。 */
    private TaskGraphTemplateView resolveTemplate(
            String agentProfileId,
            ChildJobRequest request) {
        if (request.templateRef() == null) {
            return null;
        }
        return templateService.list(agentProfileId).stream()
                .filter(template -> template.templateKey().equals(
                        request.templateRef().templateKey()))
                .filter(template -> request.templateRef().version() == null
                        ? template.status()
                        == com.funjson.metaagent.job.domain
                                .TaskGraphTemplateStatus.ACTIVE
                        : template.version()
                        == request.templateRef().version())
                .findFirst()
                .orElseThrow(() -> new RuntimeStateException(
                        "TASK_GRAPH_TEMPLATE_NOT_FOUND",
                        "Child Job template is unavailable"));
    }

    /** 在没有模板时执行受控动态规划。 */
    private TaskGraphPlan dynamicPlan(ChildJobRequest request) {
        return taskGraphPlanner.plan(new TaskGraphPlanningRequest(
                request.goal(),
                request.goal(),
                request.constraints(),
                "",
                false,
                true,
                true));
    }

    /** 序列化派生事实。 */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize Child Job derivation",
                    exception);
        }
    }
}
