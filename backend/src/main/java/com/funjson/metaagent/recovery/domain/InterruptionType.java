package com.funjson.metaagent.recovery.domain;

/**
 * 框架可以识别的中断类型。
 */
public enum InterruptionType {
    LEASE_EXPIRED,
    PROCESS_CRASH,
    PROVIDER_OR_NETWORK_FAILURE,
    USER_PAUSE,
    WAITING_HUMAN,
    UNKNOWN_SIDE_EFFECT,
    CHECKPOINT_INVALID,
    TERMINAL_STATE,
    ACTIVE_OWNER,
    UNKNOWN
}
