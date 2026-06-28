package com.funjson.metaagent.clarification.domain;

/**
 * 定义需要人工澄清的结构化原因类型。
 */
public enum ClarificationReasonType {
    /** 初始目标存在会影响执行结果的歧义。 */
    GOAL_AMBIGUOUS,
    /** TaskGraph 规划缺少安全拆解所需信息。 */
    TASK_GRAPH_UNCLEAR,
    /** Task Contract 缺少必填输入。 */
    TASK_CONTRACT_MISSING_INPUT,
    /** Tool 调用缺少必填参数。 */
    TOOL_ARGUMENT_MISSING,
    /** Skill 执行需要用户选择策略。 */
    SKILL_POLICY_CHOICE_REQUIRED,
    /** PolicyResolver 发现合同冲突，需要人工决策。 */
    POLICY_CONFLICT
}
