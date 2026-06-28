package com.funjson.metaagent.tool.application;

import java.util.List;

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
                    "file.search",
                    ToolType.RETRIEVAL,
                    "在用户授权的文件范围中检索证据。",
                    "{\"type\":\"object\",\"properties\":{\"pattern\":{\"type\":\"string\"}}}",
                    List.of("file:read"),
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
}
