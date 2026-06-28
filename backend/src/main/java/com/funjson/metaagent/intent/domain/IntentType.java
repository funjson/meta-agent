package com.funjson.metaagent.intent.domain;

/**
 * 定义 Control Kernel 可以识别的顶层用户意图。
 */
public enum IntentType {
    /** 简单问答或寒暄，同样创建单节点 Job 交给 Loop 决定最终响应。 */
    CHAT_QA,
    /** 创建新的 Job。 */
    CREATE_JOB,
    /** 暂停当前或显式指定的 Job。 */
    PAUSE_JOB,
    /** 恢复当前或显式指定的 Job。 */
    RESUME_JOB,
    /** 取消当前或显式指定的 Job。 */
    CANCEL_JOB,
    /** 查询运行状态。 */
    QUERY_STATUS,
    /** 修改当前任务约束。 */
    MODIFY_CONSTRAINTS,
    /** 回答一个已打开的 ClarificationRequest。 */
    CLARIFICATION_ANSWER,
    /** 用户消息像补充信息，但无法确定要作用到哪个等待任务。 */
    PENDING_INTERACTION_AMBIGUOUS,
    /** 用户询问当前等待项需要补充哪些信息。 */
    PENDING_INTERACTION_HELP
}
