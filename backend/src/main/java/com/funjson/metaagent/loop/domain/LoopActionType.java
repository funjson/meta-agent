package com.funjson.metaagent.loop.domain;

/**
 * LoopNode Planning 可以选择的框架级动作类型。
 */
public enum LoopActionType {
    /** 直接调用模型生成回答或下一步判断。 */
    MODEL_CALL,
    /** 调用框架工具或动态工具。 */
    TOOL_CALL,
    /** 执行 RAG 检索。 */
    RAG_QUERY,
    /** 执行 Web Search。 */
    WEB_SEARCH,
    /** 执行文件检索。 */
    FILE_SEARCH,
    /** 加载 Skill 到当前 LoopNode。 */
    SKILL_LOAD,
    /** 创建结构化澄清请求。 */
    CLARIFICATION_REQUEST,
    /** 派生 Child LoopNode。 */
    CHILD_LOOP,
    /** 派生阻塞型 Child Job。 */
    CHILD_JOB
}
