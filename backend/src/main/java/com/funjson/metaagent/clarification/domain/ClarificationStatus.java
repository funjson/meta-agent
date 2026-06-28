package com.funjson.metaagent.clarification.domain;

/**
 * 澄清会话当前状态。
 */
public enum ClarificationStatus {
    /** 已创建并等待用户回答。 */
    OPEN,
    /** 已收到用户回答。 */
    ANSWERED,
    /** 回答已被绑定回原执行点。 */
    RESOLVED,
    /** 澄清请求被取消。 */
    CANCELLED
}
