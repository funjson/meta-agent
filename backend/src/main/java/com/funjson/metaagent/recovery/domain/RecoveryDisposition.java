package com.funjson.metaagent.recovery.domain;

/**
 * 恢复策略对中断候选的处置。
 */
public enum RecoveryDisposition {
    AUTO_RESUME,
    RECONCILE_REQUIRED,
    MANUAL_REQUIRED,
    NOT_RECOVERABLE
}
