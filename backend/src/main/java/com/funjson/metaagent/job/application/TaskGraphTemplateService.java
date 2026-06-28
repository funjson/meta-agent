package com.funjson.metaagent.job.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.job.api.CreateTaskGraphTemplateRequest;
import com.funjson.metaagent.job.api.TaskGraphTemplateView;
import com.funjson.metaagent.job.application.port.out.TaskGraphTemplateStore;
import com.funjson.metaagent.job.domain.TaskGraphPlan;
import com.funjson.metaagent.job.domain.TaskGraphValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 管理 TaskGraphTemplate 版本、校验和意图标签匹配。
 */
@Service
public class TaskGraphTemplateService {

    private final TaskGraphTemplateStore store;
    private final TaskGraphValidator validator;
    private final ObjectMapper objectMapper;

    /**
     * 创建模板服务。
     *
     * @param store 模板持久化端口
     * @param validator TaskGraph 校验器
     * @param objectMapper JSON 序列化器
     */
    public TaskGraphTemplateService(
            TaskGraphTemplateStore store,
            TaskGraphValidator validator,
            ObjectMapper objectMapper) {
        this.store = store;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建并自动激活一个不可变模板版本。
     *
     * @param request 模板请求
     * @return 已创建版本
     */
    @Transactional
    public TaskGraphTemplateView createVersion(
            CreateTaskGraphTemplateRequest request) {
        TaskGraphPlan graph = validator.validate(request.graph());
        List<String> labels = normalizeLabels(request.intentLabels());
        UUID templateId = store.findTemplateId(
                        request.agentProfileId(),
                        request.templateKey())
                .orElseGet(UUID::randomUUID);
        int version = store.nextVersion(
                request.agentProfileId(),
                request.templateKey());
        String checksum = checksum(new ChecksumPayload(
                request.agentProfileId(),
                request.templateKey(),
                request.name().trim(),
                labels,
                graph));

        // 一个模板 Key 只有一个激活版本；历史版本保持不可变并转为 RETIRED。
        store.retireActiveVersions(
                request.agentProfileId(),
                request.templateKey());
        store.insert(
                templateId,
                request.agentProfileId(),
                request.templateKey(),
                version,
                request.name().trim(),
                labels,
                graph,
                checksum);
        return get(templateId, version);
    }

    /**
     * 查询指定模板版本。
     *
     * @param id 模板 ID
     * @param version 版本
     * @return 模板视图
     */
    @Transactional(readOnly = true)
    public TaskGraphTemplateView get(UUID id, int version) {
        return store.find(id, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Task graph template not found: "
                                + id + "@" + version));
    }

    /**
     * 查询 Profile 下全部模板。
     *
     * @param agentProfileId AgentProfile ID
     * @return 模板列表
     */
    @Transactional(readOnly = true)
    public List<TaskGraphTemplateView> list(
            String agentProfileId) {
        return store.findAll(agentProfileId);
    }

    /**
     * 根据 Intent 标签匹配当前激活模板。
     *
     * @param agentProfileId AgentProfile ID
     * @param intentLabels Intent 输出标签
     * @return 匹配模板
     */
    @Transactional(readOnly = true)
    public Optional<TaskGraphTemplateView> match(
            String agentProfileId,
            List<String> intentLabels) {
        Set<String> requested = normalizeLabels(intentLabels).stream()
                .collect(Collectors.toUnmodifiableSet());
        if (requested.isEmpty()) {
            return Optional.empty();
        }
        return store.findActive(agentProfileId).stream()
                .map(template -> new ScoredTemplate(
                        template,
                        template.intentLabels().stream()
                                .filter(requested::contains)
                                .count()))
                .filter(scored -> scored.score() > 0)
                .max(Comparator
                        .comparingLong(ScoredTemplate::score)
                        .thenComparing(scored ->
                                scored.template().version()))
                .map(ScoredTemplate::template);
    }

    /** 规范化标签。 */
    private List<String> normalizeLabels(List<String> labels) {
        if (labels == null) {
            return List.of();
        }
        return labels.stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    /** 计算模板内容 SHA-256。 */
    private String checksum(Object value) {
        try {
            byte[] canonical = objectMapper
                    .writeValueAsString(value)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical));
        } catch (JsonProcessingException
                 | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Unable to checksum task graph template",
                    exception);
        }
    }

    /** checksum 输入。 */
    private record ChecksumPayload(
            String agentProfileId,
            String templateKey,
            String name,
            List<String> labels,
            TaskGraphPlan graph) {
    }

    /** 模板匹配分数。 */
    private record ScoredTemplate(
            TaskGraphTemplateView template,
            long score) {
    }
}
