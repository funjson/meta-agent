package com.funjson.metaagent.tool.application;

import java.util.List;

import com.funjson.metaagent.provider.domain.ModelToolSpec;
import com.funjson.metaagent.tool.application.port.out.ToolStore;
import com.funjson.metaagent.tool.domain.ToolDefinition;
import com.funjson.metaagent.tool.domain.ToolType;
import org.springframework.stereotype.Service;

/**
 * 提供 Loop 上下文可见的框架级工具目录。
 *
 * <p>v0.1 先登记稳定工具合同，不直接执行外部脚本。后续真正的执行器可以通过
 * 相同 ToolDefinition 和 ToolInvocation 合同接入。</p>
 */
@Service
public class ToolCatalogService {

    private final ToolStore toolStore;
    private final List<ToolDefinition> frameworkTools = List.of(
            new ToolDefinition(
                    "skill.search",
                    ToolType.SKILL_SEARCH,
                    "根据当前目标和约束发现可用 Skill。",
                    "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}",
                    List.of("skill:read"),
                    true),
            new ToolDefinition(
                    "skill.load",
                    ToolType.SKILL_LOAD,
                    "把指定 Skill 作为当前 LoopNode 的局部能力加载。",
                    "{\"type\":\"object\",\"properties\":{\"skillId\":{\"type\":\"string\"}}}",
                    List.of("skill:read"),
                    true),
            new ToolDefinition(
                    "clarification.request",
                    ToolType.CLARIFICATION,
                    "当目标、工具参数或验收合同缺失时创建澄清请求。",
                    "{\"type\":\"object\",\"properties\":{\"question\":{\"type\":\"string\"}}}",
                    List.of(),
                    false),
            new ToolDefinition(
                    "rag.query",
                    ToolType.RETRIEVAL,
                    "从受控知识源检索上下文片段。",
                    "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}",
                    List.of("retrieval:read"),
                    true),
            new ToolDefinition(
                    "weather.current",
                    ToolType.RETRIEVAL,
                    "查询指定地点的当前天气和短期预报。适合“今天、现在、明天、天气、气温、降雨、风力”等强实时天气问题；不要用 web.search 替代天气查询。",
                    "{\"type\":\"object\",\"required\":[\"location\"],\"properties\":{\"location\":{\"type\":\"string\",\"description\":\"城市或地点，例如 北京\"},\"forecastDays\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":7},\"locale\":{\"type\":\"string\"}}}",
                    List.of("weather:read"),
                    true),
            new ToolDefinition(
                    "web.search",
                    ToolType.RETRIEVAL,
                    "执行受控网络搜索，返回候选来源标题、URL、摘要、发布时间和来源类型。适合最新信息、外部资料和事实核验；搜索结果本身不是最终证据。",
                    "{\"type\":\"object\",\"required\":[\"query\"],\"properties\":{\"query\":{\"type\":\"string\"},\"limit\":{\"type\":\"integer\"},\"recencyDays\":{\"type\":\"integer\"},\"domains\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"locale\":{\"type\":\"string\"}}}",
                    List.of("web:search"),
                    true),
            new ToolDefinition(
                    "web.fetch",
                    ToolType.RETRIEVAL,
                    "打开一个公开 http/https URL，抽取标题、正文、来源类型和内容哈希。只能读取 web.search 返回的具体候选来源页面；不要读取 Google/Bing/Baidu 等搜索结果页。",
                    "{\"type\":\"object\",\"required\":[\"url\"],\"properties\":{\"url\":{\"type\":\"string\"},\"maxChars\":{\"type\":\"integer\"}}}",
                    List.of("web:read"),
                    true),
            new ToolDefinition(
                    "web.extract",
                    ToolType.RETRIEVAL,
                    "打开一个公开 URL 并按 query 抽取可引用证据片段。只能读取具体来源页面，不能读取搜索结果页；用于需要引用、事实核验或研究报告的回答。",
                    "{\"type\":\"object\",\"required\":[\"url\"],\"properties\":{\"url\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"},\"maxEvidence\":{\"type\":\"integer\"}}}",
                    List.of("web:read"),
                    true),
            new ToolDefinition(
                    "file.list",
                    ToolType.RETRIEVAL,
                    "列出当前 Conversation 已上传或生成的受控文件。",
                    "{\"type\":\"object\",\"properties\":{}}",
                    List.of("file:read"),
                    true),
            new ToolDefinition(
                    "file.read",
                    ToolType.RETRIEVAL,
                    "读取当前 Conversation 中指定文本文件的内容。参数支持 fileId 或 fileName。",
                    "{\"type\":\"object\",\"properties\":{\"fileId\":{\"type\":\"string\"},\"fileName\":{\"type\":\"string\"},\"maxChars\":{\"type\":\"integer\"}}}",
                    List.of("file:read"),
                    true),
            new ToolDefinition(
                    "file.search",
                    ToolType.RETRIEVAL,
                    "在当前 Conversation 文件正文中检索片段。参数 query 为空时返回文件预览。",
                    "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"maxMatches\":{\"type\":\"integer\"}}}",
                    List.of("file:read"),
                    true),
            new ToolDefinition(
                    "file.write",
                    ToolType.FUNCTION,
                    "把文本内容写成新的受控 Conversation 文件，不覆盖原文件。",
                    "{\"type\":\"object\",\"required\":[\"fileName\",\"content\"],\"properties\":{\"fileName\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}}}",
                    List.of("file:write"),
                    true));

    /**
     * 创建 Tool Catalog Service。
     *
     * @param toolStore Tool Store
     */
    public ToolCatalogService(ToolStore toolStore) {
        this.toolStore = toolStore;
    }

    /**
     * 查询当前框架内置工具。
     *
     * @return 工具定义
     */
    public List<ToolDefinition> listFrameworkTools() {
        return frameworkTools;
    }

    /**
     * 查询当前可见的全部 Tool 定义。
     *
     * @return 框架 Tool 与脚本 Tool
     */
    public List<ToolDefinition> listAllTools() {
        return java.util.stream.Stream.concat(
                        frameworkTools.stream(),
                        toolStore.findScriptToolDefinitions().stream())
                .toList();
    }

    /**
     * 生成适合写入 Prompt 的简要工具目录。
     *
     * @return 工具摘要
     */
    public String promptSummary() {
        return listAllTools().stream()
                .map(tool -> "- %s (%s): %s".formatted(
                        tool.name(),
                        tool.type(),
                        tool.description()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("无可用工具");
    }

    /**
     * Generates a prompt summary for an explicit task-level allowlist.
     *
     * @param allowedToolIds allowed framework tool IDs
     * @return filtered prompt summary
     */
    public String promptSummary(List<String> allowedToolIds) {
        if (allowedToolIds == null || allowedToolIds.isEmpty()) {
            return "当前任务未授权使用工具";
        }
        java.util.Set<String> allowed = allowedToolIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());
        return listAllTools().stream()
                .filter(tool -> allowed.contains(tool.name()))
                .map(tool -> "- %s (%s): %s".formatted(
                        tool.name(),
                        tool.type(),
                        tool.description()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("当前任务未授权使用工具");
    }

    /**
     * 生成 Provider 原生 Function Calling 可用的工具 Schema。
     *
     * <p>Provider 函数名通常不接受点号，因此这里保留内部 toolId，并把函数名
     * 转成稳定的下划线形式。模型返回 tool_call 后 Runtime 会映射回原始 toolId。</p>
     *
     * @return 模型工具 Schema
     */
    public List<ModelToolSpec> modelToolSpecs() {
        return listAllTools().stream()
                .map(tool -> new ModelToolSpec(
                        tool.name(),
                        functionName(tool.name()),
                        tool.description(),
                        tool.inputSchemaJson()))
                .toList();
    }

    /**
     * 把内部 Tool ID 转为 Provider 安全函数名。
     */
    private String functionName(String toolId) {
        return toolId.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
