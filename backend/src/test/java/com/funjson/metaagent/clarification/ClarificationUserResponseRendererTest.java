package com.funjson.metaagent.clarification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.clarification.application.ClarificationUserResponseRenderer;
import com.funjson.metaagent.clarification.domain.ClarificationReasonType;
import com.funjson.metaagent.clarification.domain.ClarificationRequest;
import com.funjson.metaagent.clarification.domain.ClarificationSourceType;
import com.funjson.metaagent.clarification.domain.ClarificationStatus;
import com.funjson.metaagent.intent.domain.PendingInteractionCompletion;
import org.junit.jupiter.api.Test;

/**
 * Verifies that clarification contracts are rendered as user-facing Chinese,
 * not raw system field names.
 */
class ClarificationUserResponseRendererTest {

    private final ClarificationUserResponseRenderer renderer =
            new ClarificationUserResponseRenderer(new ObjectMapper());

    @Test
    void incompleteResponseUsesContractLabelsInsteadOfRawKeys() {
        String text = renderer.incomplete(
                request(contract()),
                new PendingInteractionCompletion(
                        false,
                        Map.of("name", "冯建松"),
                        List.of("background", "style"),
                        "缺少背景和风格"));

        assertThat(text)
                .contains("身份背景", "风格偏好")
                .doesNotContain("background", "style");
    }

    @Test
    void helpListsRequiredSlotsFromContract() {
        String text = renderer.help(request(contract()));

        assertThat(text)
                .contains("姓名或称呼", "使用场景", "身份背景")
                .doesNotContain("outputFormat");
    }

    /**
     * Creates a representative clarification request for renderer tests.
     */
    private ClarificationRequest request(String contractJson) {
        Instant now = Instant.parse("2026-06-28T00:00:00Z");
        return new ClarificationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                ClarificationSourceType.CONTROL_TURN,
                ClarificationReasonType.TASK_CONTRACT_MISSING_INPUT,
                ClarificationStatus.OPEN,
                "请补充信息",
                contractJson,
                "",
                null,
                "{}",
                null,
                "缺少关键输入",
                3,
                1,
                now,
                now);
    }

    /**
     * Returns a small structural clarification contract.
     */
    private String contract() {
        return """
                {
                  "version": "v1",
                  "slots": [
                    {"key": "name", "label": "姓名或称呼", "required": true, "defaultable": false},
                    {"key": "purpose", "label": "使用场景", "required": true, "defaultable": false},
                    {"key": "background", "label": "身份背景", "required": true, "defaultable": true},
                    {"key": "style", "label": "风格偏好", "required": true, "defaultable": true},
                    {"key": "outputFormat", "label": "输出形式", "required": false, "defaultable": true}
                  ]
                }
                """;
    }
}
