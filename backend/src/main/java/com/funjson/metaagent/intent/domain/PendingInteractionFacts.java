package com.funjson.metaagent.intent.domain;

import java.util.List;
import java.util.Map;

/**
 * 从用户补充中抽取的结构化事实草稿。
 *
 * <p>字段名保持稳定英文 key，便于后续写入 Task Contract、Memory 或评测系统；
 * 用户可见文本由 Control/Loop 单独渲染，不能直接暴露本对象 JSON。</p>
 *
 * @param facts 已抽取事实，例如 name、role、purpose、style、length、targetAudience
 * @param missingFields 仍缺失的字段
 * @param answerSummary 面向系统审计的补充摘要
 */
public record PendingInteractionFacts(
        Map<String, String> facts,
        List<String> missingFields,
        String answerSummary) {

    /** 校验并复制集合字段。 */
    public PendingInteractionFacts {
        facts = facts == null ? Map.of() : Map.copyOf(facts);
        missingFields = missingFields == null
                ? List.of()
                : List.copyOf(missingFields);
        answerSummary = answerSummary == null ? "" : answerSummary.trim();
    }

    /** @return 空事实对象 */
    public static PendingInteractionFacts empty() {
        return new PendingInteractionFacts(Map.of(), List.of(), "");
    }
}
