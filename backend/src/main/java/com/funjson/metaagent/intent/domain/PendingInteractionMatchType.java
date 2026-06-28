package com.funjson.metaagent.intent.domain;

/**
 * 用户新消息与等待交互候选之间的路由判断。
 */
public enum PendingInteractionMatchType {
    /** 当前消息应作为某个 ClarificationRequest 的回答。 */
    ANSWER_CLARIFICATION,
    /** 当前消息更像新的用户意图或普通对话。 */
    NEW_INTENT,
    /** 候选过多或证据不足，需要先向用户消歧。 */
    AMBIGUOUS
}
