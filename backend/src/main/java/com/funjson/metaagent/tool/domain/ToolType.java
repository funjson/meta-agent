package com.funjson.metaagent.tool.domain;

/**
 * Tool 的框架级类别。
 */
public enum ToolType {
    /** 普通可执行工具。 */
    FUNCTION,
    /** 用于发现可用 Skill 的工具。 */
    SKILL_SEARCH,
    /** 用于把 Skill 加载到当前 LoopNode 的工具。 */
    SKILL_LOAD,
    /** 检索类工具。 */
    RETRIEVAL,
    /** 需要向用户澄清的工具动作。 */
    CLARIFICATION
}
