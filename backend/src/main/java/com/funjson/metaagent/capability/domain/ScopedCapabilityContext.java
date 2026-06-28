package com.funjson.metaagent.capability.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 当前 LoopNode 可见的局部规范上下文。
 *
 * @param instructions 有序规范文本
 * @param policy 合并后的结构化策略
 * @param sourceRefs 生效来源
 */
public record ScopedCapabilityContext(
        List<String> instructions,
        Map<String, Object> policy,
        List<CapabilityRef> sourceRefs) {

    /**
     * 返回空作用域。
     *
     * @return 空上下文
     */
    public static ScopedCapabilityContext empty() {
        return new ScopedCapabilityContext(
                List.of(),
                Map.of(),
                List.of());
    }

    /**
     * 校验并复制作用域。
     */
    public ScopedCapabilityContext {
        instructions = instructions == null
                ? List.of()
                : List.copyOf(instructions);
        policy = policy == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(policy));
        sourceRefs = sourceRefs == null
                ? List.of()
                : List.copyOf(sourceRefs);
    }

    /**
     * 返回适合 Prompt 的规范摘要。
     *
     * @return 规范摘要
     */
    public String instructionSummary() {
        return instructions.isEmpty()
                ? "无局部 Skill 规范"
                : String.join("\n", instructions);
    }
}
