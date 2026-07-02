package com.funjson.metaagent.loop.domain;

/**
 * Completion judge 的结构化判定结果。
 *
 * <p>该枚举刻意比 LoopEvaluationDecision 更细：Judge 只回答“候选结果语义上是否完成”，
 * 是否还能调整、是否应失败，仍由 LoopEvaluator 根据预算和状态机统一决策。</p>
 */
public enum LoopCompletionJudgmentDecision {

    /** 候选内容已经是可以直接交付给用户的最终结果。 */
    COMPLETE,

    /** 候选内容只是过程性说明，仍需要继续执行模型、工具或证据搜集。 */
    NEED_MORE_ACTION,

    /** 候选内容缺少支撑材料或证据，不能作为最终结果。 */
    NEED_MORE_EVIDENCE,

    /** 候选内容暴露出还需要向用户澄清关键信息。 */
    NEED_CLARIFICATION,

    /** 候选内容是异常、内部日志、工具原始结果等无效用户产物。 */
    INVALID_RESULT,

    /** Judge 不可用或无法可靠解析，由规则兜底继续判断。 */
    UNKNOWN
}
