package com.funjson.metaagent.job.api;

import com.funjson.metaagent.job.domain.TaskGraphPlan;
import com.funjson.metaagent.job.domain.TaskGraphTemplateStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * TaskGraphTemplate 不可变版本视图。
 *
 * @param id 模板 ID
 * @param agentProfileId AgentProfile ID
 * @param templateKey 稳定模板 Key
 * @param version 版本
 * @param name 名称
 * @param intentLabels 匹配标签
 * @param graph 已验证 TaskGraph
 * @param checksum 内容校验和
 * @param status 版本状态
 * @param createdAt 创建时间
 */
public record TaskGraphTemplateView(
        UUID id,
        String agentProfileId,
        String templateKey,
        int version,
        String name,
        List<String> intentLabels,
        TaskGraphPlan graph,
        String checksum,
        TaskGraphTemplateStatus status,
        Instant createdAt) {

    /**
     * 复制标签集合。
     */
    public TaskGraphTemplateView {
        intentLabels = List.copyOf(intentLabels);
    }
}
