package com.funjson.metaagent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import com.funjson.metaagent.intent.domain.ExplicitIntentRuleClassifier;
import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.intent.domain.IntentRecognitionRequest;
import com.funjson.metaagent.intent.domain.IntentRiskLevel;
import com.funjson.metaagent.intent.domain.IntentType;
import org.junit.jupiter.api.Test;

/**
 * 验证显式规则分类器只处理高确定性意图。
 */
class IntentRecognitionServiceTest {

    private final ExplicitIntentRuleClassifier classifier =
            new ExplicitIntentRuleClassifier();

    @Test
    void classifiesGreetingAsChatQaThatCreatesLoopJob() {
        var recognition = classifier.classify(new IntentRecognitionRequest(
                "你好",
                "",
                null,
                false)).orElseThrow();

        assertThat(recognition.intentType()).isEqualTo(IntentType.CHAT_QA);
        assertThat(recognition.createsJob()).isTrue();
        assertThat(recognition.labels()).containsExactly("chat-qa");
    }

    @Test
    void doesNotPretendRulesCanUnderstandGeneralTasks() {
        var recognition = classifier.classify(new IntentRecognitionRequest(
                "请用 Java 和 MySQL 设计支持中断恢复与可观测的 Agent",
                "",
                null,
                false));

        assertThat(recognition).isEmpty();
    }

    @Test
    void treatsModificationIntentAsExecutableChatTask() {
        var recognition = new IntentRecognition(
                IntentType.MODIFY_CONSTRAINTS,
                0.88,
                "TEST",
                "修改上一轮产物",
                "用户希望修改已有结果，应进入新的任务循环。",
                java.util.List.of("15年经验"),
                false,
                false,
                IntentRiskLevel.LOW,
                java.util.List.of("modify-output"));

        assertThat(recognition.createsJob()).isTrue();
    }
}
