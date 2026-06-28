package com.funjson.metaagent.intent.domain;

import java.util.List;
import java.util.Map;

/**
 * 表示一次等待交互回答的合同完整性评估结果。
 *
 * <p>该对象只服务于系统编排：用户可见问题由 Control/Clarification 渲染，
 * 不会把结构化事实或缺失字段 JSON 直接暴露给用户。</p>
 *
 * @param complete 当前累计事实是否已经满足澄清合同
 * @param mergedFacts 历史补充与当前回答合并后的结构化事实
 * @param missingFields 仍缺失的稳定字段名或合同字段
 * @param answerSummary 面向系统审计的回答摘要
 */
public record PendingInteractionCompletion(
        boolean complete,
        Map<String, String> mergedFacts,
        List<String> missingFields,
        String answerSummary) {

    /** 复制集合字段，保证评估快照不可变。 */
    public PendingInteractionCompletion {
        mergedFacts = mergedFacts == null ? Map.of() : Map.copyOf(mergedFacts);
        missingFields = missingFields == null
                ? List.of()
                : List.copyOf(missingFields);
        answerSummary = answerSummary == null ? "" : answerSummary.trim();
    }
}
