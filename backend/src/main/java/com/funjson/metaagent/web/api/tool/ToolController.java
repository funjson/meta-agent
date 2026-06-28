package com.funjson.metaagent.web.api.tool;

import com.funjson.metaagent.tool.api.InvokeToolRequest;
import com.funjson.metaagent.tool.api.ToolInvocationView;
import com.funjson.metaagent.tool.application.ToolCatalogService;
import com.funjson.metaagent.tool.application.ToolExecutionService;
import com.funjson.metaagent.tool.application.ToolInvocationCommand;
import com.funjson.metaagent.tool.domain.ToolDefinition;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tool Runtime HTTP Adapter。
 */
@RestController
@RequestMapping("/api/v1/tools")
public class ToolController {

    private final ToolCatalogService catalogService;
    private final ToolExecutionService executionService;

    /**
     * 创建 Tool Controller。
     *
     * @param catalogService Tool 目录
     * @param executionService Tool 执行器
     */
    public ToolController(
            ToolCatalogService catalogService,
            ToolExecutionService executionService) {
        this.catalogService = catalogService;
        this.executionService = executionService;
    }

    /**
     * 查询当前 Tool 目录。
     *
     * @return Tool 定义列表
     */
    @GetMapping
    public List<ToolDefinition> list() {
        return catalogService.listAllTools();
    }

    /**
     * 调用一个 Tool。
     *
     * @param toolId Tool ID
     * @param idempotencyKey 幂等键
     * @param request 请求
     * @return 调用结果
     */
    @PostMapping("/{toolId}/invoke")
    public ToolInvocationView invoke(
            @PathVariable String toolId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InvokeToolRequest request) {
        return executionService.invoke(new ToolInvocationCommand(
                toolId,
                request.arguments(),
                idempotencyKey,
                null,
                null,
                null,
                null,
                null));
    }
}
