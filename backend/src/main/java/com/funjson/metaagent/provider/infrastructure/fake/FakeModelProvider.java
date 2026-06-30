package com.funjson.metaagent.provider.infrastructure.fake;

import java.time.Duration;
import java.util.UUID;

import com.funjson.metaagent.provider.domain.ModelProvider;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.provider.domain.ModelResponse;
import com.funjson.metaagent.provider.domain.ModelToolCall;
import com.funjson.metaagent.provider.domain.ModelToolSpec;
import com.funjson.metaagent.provider.infrastructure.persistence.mybatis.ModelCallRepository;
import org.springframework.stereotype.Component;

/**
 * 用于离线测试和确定性回归的 Fake Provider。
 */
@Component
public class FakeModelProvider implements ModelProvider {

    private final ModelCallRepository modelCallRepository;

    /**
     * 创建 Fake Provider。
     *
     * @param modelCallRepository 模型调用审计 Repository
     */
    public FakeModelProvider(ModelCallRepository modelCallRepository) {
        this.modelCallRepository = modelCallRepository;
    }

    @Override
    public String providerId() {
        return "fake";
    }

    @Override
    public boolean supportsNativeToolCalling() {
        return true;
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        long started = System.nanoTime();
        String normalizedGoal = request.inputSummary().replaceAll("\\s+", " ").trim();
        ModelResponse response = nativeToolResponse(request, normalizedGoal);
        if (response == null) {
            String content = "loop.action-planning".equals(
                    request.prompt().promptId())
                    ? actionPlanFor(request)
                    : contentFor(normalizedGoal);
            response = new ModelResponse(
                    providerId(),
                    "fake-deterministic-v1",
                    content,
                    "STOP");
        }
        recordCall(request, response, started);
        return response;
    }

    /**
     * 在原生 Tool Calling 冒烟中返回确定性工具调用。
     *
     * @param request 模型请求
     * @param normalizedGoal 归一化目标
     * @return 工具调用响应；不需要工具时返回 null
     */
    private ModelResponse nativeToolResponse(
            ModelRequest request,
            String normalizedGoal) {
        if (request.tools().isEmpty()
                || "loop.action-planning".equals(request.prompt().promptId())) {
            return null;
        }
        if (isWeatherGoal(normalizedGoal)
                && hasTool(request, "weather.current")) {
            return toolCallResponse(
                    request,
                    "weather.current",
                    java.util.Map.of(
                            "location", weatherLocation(normalizedGoal),
                            "forecastDays", 3,
                            "locale", "zh-CN"));
        }
        if (normalizedGoal.matches("(?is).*(搜索|搜一下|网上|网络|最新|新闻|资料|外部事实|核验|search|web|latest|news|external|research|weather).*")
                && hasTool(request, "web.search")) {
            return toolCallResponse(
                    request,
                    "web.search",
                    java.util.Map.of(
                            "query", normalizedGoal,
                            "limit", 5));
        }
        if (normalizedGoal.matches("(?s).*(上传文件|附件|根据.*文件|读取文件|文件内容).*")
                && request.prompt().userMessage()
                .matches("(?s).*id=[0-9a-fA-F-]{36}.*")
                && hasTool(request, "file.read")) {
            String fileId = request.prompt().userMessage().replaceFirst(
                    "(?s).*id=([0-9a-fA-F-]{36}).*",
                    "$1");
            return toolCallResponse(
                    request,
                    "file.read",
                    java.util.Map.of(
                            "fileId", fileId,
                            "maxChars", 30000));
        }
        if (normalizedGoal.matches("(?s).*(上传文件|附件|文件列表|有哪些文件).*")
                && hasTool(request, "file.list")) {
            return toolCallResponse(
                    request,
                    "file.list",
                    java.util.Map.of());
        }
        return null;
    }

    /**
     * 构造 fake tool_call 响应。
     */
    private ModelResponse toolCallResponse(
            ModelRequest request,
            String toolId,
            java.util.Map<String, Object> arguments) {
        ModelToolSpec spec = request.tools().stream()
                .filter(tool -> tool.toolId().equals(toolId))
                .findFirst()
                .orElseThrow();
        return new ModelResponse(
                providerId(),
                "fake-deterministic-v1",
                "",
                "TOOL_CALLS",
                java.util.List.of(new ModelToolCall(
                        UUID.randomUUID().toString(),
                        spec.toolId(),
                        spec.functionName(),
                        arguments)),
                "");
    }

    /**
     * @return 请求是否暴露了指定工具
     */
    private boolean hasTool(ModelRequest request, String toolId) {
        return request.tools().stream()
                .anyMatch(tool -> tool.toolId().equals(toolId));
    }

    /**
     * 为 ReAct Planning Prompt 返回合法结构化动作。
     *
     * @param request 模型请求
     * @return LoopPlan JSON
     */
    /**
     * Detects weather goals without depending on terminal display encoding.
     *
     * @param value normalized user goal
     * @return whether the goal asks for weather
     */
    private boolean isWeatherGoal(String value) {
        return value.matches("(?is).*(\\u5929\\u6c14|\\u6c14\\u6e29|"
                + "\\u964d\\u96e8|\\u98ce\\u529b|weather|forecast).*");
    }

    /**
     * Extracts a tiny deterministic location for fake tool calls.
     *
     * @param value normalized user goal
     * @return location argument for weather.current
     */
    private String weatherLocation(String value) {
        if (value.contains("\u5317\u4eac")) {
            return "\u5317\u4eac";
        }
        if (value.contains("\u4e0a\u6d77")) {
            return "\u4e0a\u6d77";
        }
        return value;
    }

    /**
     * Returns a valid structured action for the ReAct Planning prompt.
     *
     * @param request model request
     * @return LoopPlan JSON
     */
    private String actionPlanFor(ModelRequest request) {
        String userPrompt = request.prompt().userMessage();
        String goal = userPrompt.replaceFirst(
                "(?s).*目标：\\s*([^\\n]+).*",
                "$1");
        if (!userPrompt.contains("当前反馈：\n无")) {
            return """
                    {
                      "actionType": "MODEL_CALL",
                      "summary": "工具 Observation 已进入上下文，生成最终用户回复",
                      "completionCriterion": "返回可直接展示给用户的自然语言结果",
                      "maxTokens": 512
                    }
                    """;
        }
        if (isWeatherGoal(goal)) {
            return """
                    {
                      "actionType": "TOOL_CALL",
                      "toolId": "weather.current",
                      "summary": "需要查询实时天气工具",
                      "completionCriterion": "天气 Observation 进入上下文",
                      "arguments": {
                        "location": "%s",
                        "forecastDays": 3,
                        "locale": "zh-CN"
                      }
                    }
                    """.formatted(weatherLocation(goal).replace("\"", "\\\""));
        }
        if (goal.matches("(?is).*(搜索|搜一下|网上|网络|最新|新闻|资料|外部事实|核验|search|web|latest|news|external|research).*")) {
            return """
                    {
                      "actionType": "WEB_SEARCH",
                      "toolId": "web.search",
                      "summary": "需要先执行网络搜索获取外部资料",
                      "completionCriterion": "搜索结果进入 Observation",
                      "arguments": {
                        "query": "%s",
                        "limit": 5
                      }
                    }
                    """.formatted(goal.replace("\"", "\\\""));
        }
        if (goal.matches("(?s).*(上传文件|附件|根据.*文件|读取文件|文件内容).*")
                && userPrompt.matches("(?s).*id=[0-9a-fA-F-]{36}.*")) {
            String fileId = userPrompt.replaceFirst(
                    "(?s).*id=([0-9a-fA-F-]{36}).*",
                    "$1");
            return """
                    {
                      "actionType": "TOOL_CALL",
                      "toolId": "file.read",
                      "summary": "先读取当前 Conversation 的相关文件",
                      "completionCriterion": "文件正文进入 Observation",
                      "arguments": {
                        "fileId": "%s",
                        "maxChars": 30000
                      }
                    }
                    """.formatted(fileId);
        }
        if (goal.matches("(?s).*(上传文件|附件|文件列表|有哪些文件).*")) {
            return """
                    {
                      "actionType": "TOOL_CALL",
                      "toolId": "file.list",
                      "summary": "先列出当前 Conversation 的文件",
                      "completionCriterion": "文件清单进入 Observation",
                      "arguments": {}
                    }
                    """;
        }
        return """
                {
                  "actionType": "MODEL_CALL",
                  "summary": "当前上下文足够，直接生成用户可见回复",
                  "completionCriterion": "返回可直接展示给用户的自然语言结果",
                  "maxTokens": 512
                }
                """;
    }

    /**
     * 生成面向用户的确定性回复。
     *
     * @param normalizedGoal 归一化目标
     * @return 回复内容
     */
    private String contentFor(String normalizedGoal) {
        if (normalizedGoal.matches("^(你好|您好|嗨|hello|hi|hey)[呀啊。.！! ]*$")) {
            return "你好呀，我在。你可以直接告诉我想完成什么，我会继续帮你推进。";
        }
        return """
                我已经收到你的目标：%s

                这是一次离线确定性回复，用于验证聊天、任务执行和结果回传链路。真实模型启用后，我会根据上下文生成更具体的结果。
                """.formatted(normalizedGoal).trim();
    }

    /**
     * 保存与真实 Provider 相同结构的模型调用审计。
     *
     * @param request 模型请求
     * @param response 模型响应
     * @param started 开始纳秒
     */
    private void recordCall(
            ModelRequest request,
            ModelResponse response,
            long started) {
        modelCallRepository.insert(
                UUID.randomUUID(),
                request.taskRunId(),
                request.loopNodeId(),
                providerId(),
                response.model(),
                request.prompt().contentHash(),
                request.prompt().promptId(),
                request.prompt().version(),
                request.prompt().contentHash(),
                "COMPLETED",
                null,
                null,
                Duration.ofNanos(System.nanoTime() - started).toMillis(),
                null);
    }
}
