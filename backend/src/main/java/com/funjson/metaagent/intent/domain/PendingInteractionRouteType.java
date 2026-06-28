package com.funjson.metaagent.intent.domain;

/**
 * 用户消息相对等待交互的结构化路由结果。
 */
public enum PendingInteractionRouteType {
    /** 用户消息应作为某个 ClarificationRequest 的回答。 */
    ANSWER_CLARIFICATION,
    /** 用户选择了上一轮消歧中的某个等待项。 */
    SELECT_PENDING_INTERACTION,
    /** 用户消息更像新的任务、普通问答或话题切换。 */
    NEW_INTENT,
    /** 信息像补充材料，但目标等待项不明确。 */
    AMBIGUOUS,
    /** 用户在询问当前等待项需要补充什么。 */
    EXPLAIN_PENDING_REQUIREMENTS,
    /** 用户消息是暂停、恢复、取消或查询等控制命令。 */
    CONTROL_COMMAND
}
