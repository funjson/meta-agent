package com.funjson.metaagent.observability.application.port.out;

import java.util.UUID;

import com.funjson.metaagent.observability.api.AgentPathView;

/**
 * 定义读取 Control Path 与 Execution Path 投影的查询端口。
 */
public interface AgentPathQuery {

    /** @return Conversation 的完整 Agent Path */
    AgentPathView find(UUID conversationId);
}
