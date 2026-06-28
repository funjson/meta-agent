package com.funjson.metaagent.web.api.observability;

import com.funjson.metaagent.observability.api.AgentPathView;
import com.funjson.metaagent.observability.application.port.out.AgentPathQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Agent Path 跨聚合读模型的 HTTP Adapter。
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class AgentPathController {

    private final AgentPathQuery agentPathQuery;

    /**
     * 创建 Agent Path Controller。
     *
     * @param agentPathQuery Agent Path 查询端口
     */
    public AgentPathController(AgentPathQuery agentPathQuery) {
        this.agentPathQuery = agentPathQuery;
    }

    /**
     * 查询 Conversation 关联的决策与执行路径。
     *
     * @param conversationId Conversation ID
     * @return Agent Path
     */
    @GetMapping("/{conversationId}/agent-path")
    public AgentPathView path(@PathVariable UUID conversationId) {
        return agentPathQuery.find(conversationId);
    }
}
