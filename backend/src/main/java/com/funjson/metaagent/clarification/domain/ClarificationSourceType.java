package com.funjson.metaagent.clarification.domain;

/**
 * 标识澄清请求来源于哪一层执行边界。
 */
public enum ClarificationSourceType {
    /** Control 初始化阶段。 */
    CONTROL_TURN,
    /** Job 的 TaskGraph 规划阶段。 */
    TASK_GRAPH,
    /** Task 执行合同阶段。 */
    TASK,
    /** LoopNode 局部执行阶段。 */
    LOOP_NODE,
    /** Tool 调用参数阶段。 */
    TOOL_CALL,
    /** Skill 选择或执行阶段。 */
    SKILL,
    /** 策略合并阶段。 */
    POLICY_RESOLVER
}
