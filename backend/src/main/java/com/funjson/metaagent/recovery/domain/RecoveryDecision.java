package com.funjson.metaagent.recovery.domain;

/**
 * 可审计恢复决策。
 *
 * @param interruptionType 中断类型
 * @param disposition 处置
 * @param code 稳定决策码
 * @param summary 安全摘要
 */
public record RecoveryDecision(
        InterruptionType interruptionType,
        RecoveryDisposition disposition,
        String code,
        String summary) {
}
