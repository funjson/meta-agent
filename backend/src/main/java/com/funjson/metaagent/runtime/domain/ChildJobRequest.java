package com.funjson.metaagent.runtime.domain;

import java.util.List;

/**
 * LoopNode 请求 Job 层物化阻塞型子 Job 的中立合同。
 *
 * @param goal 子 Job 目标
 * @param constraints 不可放宽的约束摘要
 * @param templateRef 可选 TaskGraphTemplate 引用
 * @param subagentProfileRef 可选 SubagentProfile 引用
 * @param dynamicPlanningInstruction 无模板时的受控规划说明
 * @param contractContribution 任务合同补充
 * @param capabilityRequest 能力申请
 * @param idempotencyKey 派生幂等键
 * @param sourceSkillId 来源 Skill ID
 * @param sourceSkillVersion 来源 Skill 版本
 */
public record ChildJobRequest(
        String goal,
        List<String> constraints,
        TaskGraphTemplateRef templateRef,
        SubagentProfileRef subagentProfileRef,
        String dynamicPlanningInstruction,
        ContractContribution contractContribution,
        CapabilityRequest capabilityRequest,
        String idempotencyKey,
        String sourceSkillId,
        Integer sourceSkillVersion) {

    /**
     * 校验阻塞型子 Job 请求。
     */
    public ChildJobRequest {
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("Child Job goal is required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Child Job idempotency key is required");
        }
        constraints = constraints == null
                ? List.of()
                : List.copyOf(constraints);
        dynamicPlanningInstruction =
                dynamicPlanningInstruction == null
                        ? ""
                        : dynamicPlanningInstruction;
        contractContribution = contractContribution == null
                ? ContractContribution.empty()
                : contractContribution;
        capabilityRequest = capabilityRequest == null
                ? CapabilityRequest.none()
                : capabilityRequest;
        if (templateRef == null
                && dynamicPlanningInstruction.isBlank()) {
            throw new IllegalArgumentException(
                    "Child Job requires a template or dynamic planning instruction");
        }
        if (sourceSkillVersion != null && sourceSkillVersion < 1) {
            throw new IllegalArgumentException(
                    "Source Skill version must be positive");
        }
    }
}
