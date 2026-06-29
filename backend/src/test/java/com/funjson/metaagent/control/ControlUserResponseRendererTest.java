package com.funjson.metaagent.control;

import static org.assertj.core.api.Assertions.assertThat;

import com.funjson.metaagent.control.application.ControlUserResponseRenderer;
import com.funjson.metaagent.intent.domain.IntentType;
import org.junit.jupiter.api.Test;

/**
 * Verifies the boundary between Control audit decisions and chat-room messages.
 */
class ControlUserResponseRendererTest {

    private final ControlUserResponseRenderer renderer =
            new ControlUserResponseRenderer();

    @Test
    void commandIntentUsesCommandAckMessageType() {
        assertThat(renderer.messageType(IntentType.PAUSE_JOB))
                .isEqualTo("CONTROL_COMMAND_ACK");
        assertThat(renderer.messageText(IntentType.PAUSE_JOB, "内部摘要"))
                .contains("控制命令已记录")
                .doesNotContain("内部摘要");
    }

    @Test
    void clarificationAnswerWithoutJobKeepsUserFacingSummary() {
        assertThat(renderer.messageType(IntentType.CLARIFICATION_ANSWER))
                .isEqualTo("CLARIFICATION_DISAMBIGUATION");
        assertThat(renderer.messageText(
                IntentType.CLARIFICATION_ANSWER,
                "当前没有找到正在等待补充的任务。"))
                .isEqualTo("当前没有找到正在等待补充的任务。");
    }
}
