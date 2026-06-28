package com.funjson.metaagent.runtime.domain;

/**
 * 策略继承与冲突处理结果。
 */
public enum PolicyResolutionStatus {
    ALLOWED,
    WAITING_APPROVAL,
    WAITING_HUMAN,
    REJECTED
}
