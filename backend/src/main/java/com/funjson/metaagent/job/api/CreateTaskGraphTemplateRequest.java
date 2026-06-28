package com.funjson.metaagent.job.api;

import com.funjson.metaagent.job.domain.TaskGraphPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 创建 TaskGraphTemplate 新版本的请求。
 *
 * @param agentProfileId AgentProfile ID
 * @param templateKey Profile 内稳定模板 Key
 * @param name 模板名称
 * @param intentLabels 意图匹配标签
 * @param graph TaskGraph 定义
 */
public record CreateTaskGraphTemplateRequest(
        @NotBlank String agentProfileId,
        @NotBlank String templateKey,
        @NotBlank String name,
        List<String> intentLabels,
        @NotNull TaskGraphPlan graph) {
}
