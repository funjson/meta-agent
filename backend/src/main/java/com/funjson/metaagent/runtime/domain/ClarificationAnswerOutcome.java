package com.funjson.metaagent.runtime.domain;

import java.util.UUID;

/**
 * 用户回答澄清请求后，回传给原始 LoopNode 的结构化结果。
 *
 * @param clarificationRequestId 澄清请求 ID
 * @param question 原始问题
 * @param answer 用户回答
 */
public record ClarificationAnswerOutcome(
        UUID clarificationRequestId,
        String question,
        String answer) {
}
