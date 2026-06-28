package com.funjson.metaagent.observability.api;

import java.time.Instant;

/**
 * Agent Path 中一个可审计的结构化节点。
 *
 * @param id 节点 ID
 * @param parentId 父节点 ID
 * @param nodeType 节点类型
 * @param label 展示标签
 * @param status 状态
 * @param summary 结构化摘要
 * @param occurredAt 发生时间
 */
public record AgentPathNode(
        String id,
        String parentId,
        String nodeType,
        String label,
        String status,
        String summary,
        Instant occurredAt) {
}
