package com.funjson.metaagent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.funjson.metaagent.intent.application.PendingInteractionCompletionPolicy;
import com.funjson.metaagent.intent.domain.PendingInteractionFacts;
import com.funjson.metaagent.loop.domain.RuntimeClarificationContractBuilder;
import org.junit.jupiter.api.Test;

/**
 * 验证 Loop 运行时自然语言澄清会补齐结构化合同。
 */
class RuntimeClarificationContractBuilderTest {

    private final RuntimeClarificationContractBuilder builder =
            new RuntimeClarificationContractBuilder();

    @Test
    void buildsResumeContractWithDefaultableRuntimeSlots() {
        String contract = builder.build(
                "你能帮我生成一个个人简历么",
                "请补充姓名、学历、工作经验、岗位和风格。");

        assertThat(contract)
                .contains("\"version\": \"runtime-v1\"")
                .contains("\"key\": \"role\"")
                .contains("\"label\": \"目标岗位或角色\"")
                .contains("\"defaultable\": true");
    }

    @Test
    void defaultConsentCompletesResumeRuntimeContract() {
        String contract = builder.build(
                "你能帮我生成一个个人简历么",
                "请补充姓名、学历、工作经验、岗位和风格。");
        PendingInteractionCompletionPolicy policy =
                new PendingInteractionCompletionPolicy();

        var completion = policy.assess(
                "请补充姓名、学历、工作经验、岗位和风格。",
                "生成简历缺少输入",
                contract,
                new PendingInteractionFacts(
                        Map.of("userAcceptedDefaults", "true"),
                        java.util.List.of("role", "style"),
                        "用户要求剩余内容按默认处理"),
                Map.of(
                        "name", "冯建松",
                        "educationLevel", "大专",
                        "experience", "10年工作经验"));

        assertThat(completion.complete()).isTrue();
        assertThat(completion.missingFields()).isEmpty();
    }
}
