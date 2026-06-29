package com.funjson.metaagent.context.domain;

/**
 * Loop 上下文块的结构化类型。
 */
public enum ContextBlockType {
    /** 系统层角色和执行规范。 */
    SYSTEM,
    /** 用户原始目标或 Task 目标。 */
    USER_GOAL,
    /** Conversation 可见消息和会话级事实。 */
    CONVERSATION,
    /** Conversation 或上游 Task 摘要。 */
    MEMORY,
    /** 当前打开的等待项、澄清请求或授权请求。 */
    PENDING_INTERACTION,
    /** Job / Task 合同与验收要求。 */
    CONTRACT,
    /** 上一轮动作后的 Observation。 */
    OBSERVATION,
    /** Skill 或 Capability 注入的局部规范。 */
    CAPABILITY,
    /** 当前可用工具目录。 */
    TOOL_CATALOG,
    /** 当前 Conversation 可用的文件附件。 */
    FILE,
    /** 权限、预算、验收和递归边界。 */
    CONSTRAINT
}
