package com.funjson.metaagent.job.infrastructure.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.job.application.port.out.TaskGraphPlanningPort;
import com.funjson.metaagent.job.domain.TaskGraphNodePlan;
import com.funjson.metaagent.job.domain.TaskGraphPlan;
import com.funjson.metaagent.job.domain.TaskGraphPlanningRequest;
import com.funjson.metaagent.task.domain.TaskStatus;
import com.funjson.metaagent.prompt.application.PromptRegistry;
import com.funjson.metaagent.prompt.domain.PromptUseCase;
import com.funjson.metaagent.provider.application.ModelProviderRegistry;
import com.funjson.metaagent.provider.application.port.out.ProviderSecretPort;
import com.funjson.metaagent.provider.domain.ModelRequest;
import org.springframework.stereotype.Component;

/**
 * 使用真实模型和版本化 Prompt 生成复合任务的结构化 Task Graph 候选。
 *
 * <p>该适配器不决定最终调度状态。模型输出解析失败时返回空，由 Job 层
 * Application 降级到 WAITING_HUMAN，避免保存未经验证的任务图。</p>
 */
@Component
public class ModelTaskGraphPlanningAdapter
        implements TaskGraphPlanningPort {

    private final ModelProviderRegistry providerRegistry;
    private final ProviderSecretPort secretStore;
    private final PromptRegistry promptRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 创建模型 Task Graph 规划适配器。
     *
     * @param providerRegistry 模型 Provider Registry
     * @param secretStore Provider Secret Store
     * @param promptRegistry Prompt Registry
     * @param objectMapper JSON 解析器
     */
    public ModelTaskGraphPlanningAdapter(
            ModelProviderRegistry providerRegistry,
            ProviderSecretPort secretStore,
            PromptRegistry promptRegistry,
            ObjectMapper objectMapper) {
        this.providerRegistry = providerRegistry;
        this.secretStore = secretStore;
        this.promptRegistry = promptRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用 DeepSeek 生成 Task Graph JSON。
     *
     * @param request 规划请求
     * @return 成功解析的候选图
     */
    @Override
    public Optional<TaskGraphPlan> plan(
            TaskGraphPlanningRequest request) {
        if (!request.modelPlanningAllowed()
                || !secretStore.configured()) {
            return Optional.empty();
        }
        try {
            var prompt = promptRegistry.render(
                    PromptUseCase.CONTROL_TASK_GRAPH,
                    Map.of(
                            "goalSummary", request.goalSummary(),
                            "constraints", objectMapper.writeValueAsString(
                                    request.constraints()),
                            "userRequest", request.originalRequest()));
            var response = providerRegistry.require("deepseek")
                    .generate(new ModelRequest(
                            null,
                            null,
                            abbreviate(request.originalRequest(), 180),
                            prompt,
                            1400));
            return Optional.of(parse(response.content()));
        } catch (Exception exception) {
            // 规划失败不能产生半可信 Task；Job 层会转为可解释的人工等待态。
            return Optional.empty();
        }
    }

    /**
     * 将模型 JSON 转换为初始状态明确的 Task Graph。
     *
     * @param content 模型响应
     * @return 候选 Task Graph
     */
    private TaskGraphPlan parse(String content) {
        try {
            JsonNode root = objectMapper.readTree(
                    stripCodeFence(content));
            JsonNode tasks = root.path("tasks");
            if (!tasks.isArray()) {
                throw new IllegalArgumentException(
                        "Task graph response has no tasks array");
            }

            List<TaskGraphNodePlan> nodes = new ArrayList<>();
            for (JsonNode task : tasks) {
                List<String> dependencies = readStrings(
                        task.path("dependsOn"));
                nodes.add(new TaskGraphNodePlan(
                        task.path("key").asText(),
                        task.path("title").asText(),
                        task.path("goal").asText(),
                        dependencies.isEmpty()
                                ? TaskStatus.READY
                                : TaskStatus.BLOCKED,
                        // Task 统一进入 LoopRun；配置型流程已在 Job 层展开为 TaskGraph。
                        "LOOP",
                        dependencies));
            }
            return new TaskGraphPlan(
                    "MODEL_DECOMPOSITION",
                    root.path("summary").asText(
                            "模型生成复合任务依赖图。"),
                    nodes,
                    null);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Task graph model returned an invalid contract",
                    exception);
        }
    }

    /**
     * 读取字符串数组。
     *
     * @param node JSON 数组
     * @return 字符串列表
     */
    private List<String> readStrings(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    /**
     * 移除模型可能附加的 Markdown JSON 围栏。
     *
     * @param value 模型响应
     * @return 纯 JSON
     */
    private String stripCodeFence(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst(
                    "^```(?:json)?\\s*",
                    "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    /**
     * 截断非敏感审计摘要。
     *
     * @param value 原始值
     * @param maxLength 最大长度
     * @return 摘要
     */
    private String abbreviate(String value, int maxLength) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength - 3) + "...";
    }
}
