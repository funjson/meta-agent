package com.funjson.metaagent.observability.api;

import java.util.List;
import java.util.UUID;

/**
 * Conversation 的完整 Agent Path 投影。
 *
 * @param conversationId Conversation ID
 * @param nodes 路径节点
 */
public record AgentPathView(
        UUID conversationId,
        List<AgentPathNode> nodes) {
}
