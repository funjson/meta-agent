package com.funjson.metaagent.tool.domain;

/**
 * ToolInvocation 的生命周期状态。
 */
public enum ToolInvocationStatus {
    /** 已持久化但尚未执行。 */
    PENDING,
    /** 正在执行外部动作。 */
    RUNNING,
    /** 已完成并生成 ToolResult。 */
    COMPLETED,
    /** 执行失败。 */
    FAILED,
    /** 因缺少参数等待用户澄清。 */
    WAITING_HUMAN
}
