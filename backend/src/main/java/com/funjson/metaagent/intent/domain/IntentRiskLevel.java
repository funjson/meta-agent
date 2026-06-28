package com.funjson.metaagent.intent.domain;

/**
 * 表示意图进入 Control 调度前的初步风险等级。
 */
public enum IntentRiskLevel {
    /** 普通对话或低风险只读任务。 */
    LOW,
    /** 需要边界检查或可能产生有限副作用。 */
    MEDIUM,
    /** 涉及高影响副作用或必须人工确认。 */
    HIGH
}
