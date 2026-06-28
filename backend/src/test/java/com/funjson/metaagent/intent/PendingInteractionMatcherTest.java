package com.funjson.metaagent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.intent.application.PendingInteractionMatcher;
import com.funjson.metaagent.intent.domain.PendingInteractionCandidate;
import com.funjson.metaagent.intent.domain.PendingInteractionMatchType;
import org.junit.jupiter.api.Test;

/**
 * 验证等待交互匹配不会盲目绑定最近 Clarification。
 */
class PendingInteractionMatcherTest {

    private final PendingInteractionMatcher matcher =
            new PendingInteractionMatcher();

    @Test
    void routesToNewIntentWhenUserClearlyStartsNewTask() {
        var result = matcher.match(
                "先不管那个，帮我生成一个新的宣传文案",
                List.of(candidate("请补充你的姓名和用途")));

        assertThat(result.matchType())
                .isEqualTo(PendingInteractionMatchType.NEW_INTENT);
    }

    @Test
    void asksForDisambiguationWhenAnswerCouldTargetManyWaitingJobs() {
        var result = matcher.match(
                "我的风格要正式一点",
                List.of(
                        candidate("请补充自我介绍的用途和风格"),
                        candidate("请补充口播稿的渠道和风格")));

        assertThat(result.matchType())
                .isEqualTo(PendingInteractionMatchType.AMBIGUOUS);
    }

    @Test
    void bindsSingleCandidateOnlyWhenMessageLooksLikeAnAnswer() {
        var target = candidate("请补充目标对象、用途和风格");

        var result = matcher.match(
                "用途是面试，风格正式一点",
                List.of(target));

        assertThat(result.matchType())
                .isEqualTo(PendingInteractionMatchType.ANSWER_CLARIFICATION);
        assertThat(result.targetId()).isEqualTo(target.id());
    }

    /**
     * 创建等待候选。
     *
     * @param question 澄清问题
     * @return 候选
     */
    private PendingInteractionCandidate candidate(String question) {
        return new PendingInteractionCandidate(
                UUID.randomUUID(),
                "CLARIFICATION",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                question,
                question);
    }
}
