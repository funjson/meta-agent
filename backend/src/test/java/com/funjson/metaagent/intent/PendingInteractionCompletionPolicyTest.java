package com.funjson.metaagent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.funjson.metaagent.intent.application.PendingInteractionCompletionPolicy;
import com.funjson.metaagent.intent.domain.PendingInteractionFacts;
import org.junit.jupiter.api.Test;

/**
 * 验证澄清回答必须通过结构化合同完整性评估后才能恢复执行。
 */
class PendingInteractionCompletionPolicyTest {

    private final PendingInteractionCompletionPolicy policy =
            new PendingInteractionCompletionPolicy();

    @Test
    void keepsClarificationOpenWhenOnlyNameIsKnown() {
        var completion = policy.assess(
                "请补充目标对象/背景、使用场景、必须包含或避免的内容，以及期望输出形式、长度或风格。",
                "自我介绍缺少关键输入",
                "{}",
                new PendingInteractionFacts(
                        Map.of("name", "冯建松"),
                        java.util.List.of(),
                        "用户补充姓名"),
                Map.of());

        assertThat(completion.complete()).isFalse();
        assertThat(completion.missingFields())
                .contains("background", "purpose", "style", "length");
    }

    @Test
    void completesWhenAccumulatedFactsSatisfyQuestionContract() {
        Map<String, String> accumulated = Map.of(
                "name", "冯建松",
                "purpose", "求职");
        PendingInteractionFacts current = new PendingInteractionFacts(
                Map.of(
                        "role", "软件开发",
                        "experience", "10年经验",
                        "style", "正式",
                        "length", "100字",
                        "noSpecialRequirements", "true"),
                java.util.List.of("mustInclude"),
                "用户补齐求职自我介绍关键字段");

        var completion = policy.assess(
                "请补充目标对象/背景、使用场景、必须包含或避免的内容，以及期望输出形式、长度或风格。",
                "自我介绍缺少关键输入",
                "{}",
                current,
                accumulated);

        assertThat(completion.complete()).isTrue();
        assertThat(completion.mergedFacts())
                .containsEntry("name", "冯建松")
                .containsEntry("purpose", "求职")
                .containsEntry("role", "软件开发")
                .containsEntry("experience", "10年经验");
        assertThat(completion.missingFields()).isEmpty();
    }

    @Test
    void defaultConsentSatisfiesDefaultableContractSlots() {
        String contract = """
                {
                  "version": "v1",
                  "slots": [
                    {"key": "name", "label": "姓名", "required": true, "defaultable": false, "aliases": ["姓名", "name"]},
                    {"key": "purpose", "label": "使用场景", "required": true, "defaultable": false, "aliases": ["purpose", "useCase", "用途", "场景"]},
                    {"key": "background", "label": "身份背景", "required": true, "defaultable": true, "aliases": ["background", "role", "身份", "背景"]},
                    {"key": "style", "label": "风格", "required": true, "defaultable": true, "aliases": ["style", "风格"]},
                    {"key": "length", "label": "长度", "required": true, "defaultable": true, "aliases": ["length", "长度"]}
                  ]
                }
                """;

        var completion = policy.assess(
                "自然问题",
                "阻塞摘要",
                contract,
                new PendingInteractionFacts(
                        Map.of(
                                "purpose", "自我介绍",
                                "userAcceptedDefaults", "true"),
                        java.util.List.of("background", "style", "length"),
                        "用户允许默认补齐"),
                Map.of("name", "冯建松"));

        assertThat(completion.complete()).isTrue();
        assertThat(completion.missingFields()).isEmpty();
    }

    @Test
    void defaultConsentDoesNotSatisfyNonDefaultableContractSlots() {
        String contract = """
                {
                  "version": "v1",
                  "slots": [
                    {"key": "name", "label": "姓名", "required": true, "defaultable": false, "aliases": ["姓名", "name"]},
                    {"key": "purpose", "label": "使用场景", "required": true, "defaultable": false, "aliases": ["purpose", "用途", "场景"]},
                    {"key": "style", "label": "风格", "required": true, "defaultable": true, "aliases": ["style", "风格"]}
                  ]
                }
                """;

        var completion = policy.assess(
                "自然问题",
                "阻塞摘要",
                contract,
                new PendingInteractionFacts(
                        Map.of("userAcceptedDefaults", "true"),
                        java.util.List.of("name", "purpose", "style"),
                        "用户只允许默认补齐"),
                Map.of());

        assertThat(completion.complete()).isFalse();
        assertThat(completion.missingFields())
                .containsExactlyInAnyOrder("name", "purpose");
    }

    @Test
    void defaultConsentSatisfiesSoftSlotsButKeepsBlockingSlotsStrict() {
        String contract = """
                {
                  "version": "v1",
                  "slots": [
                    {"key": "name", "label": "姓名", "required": true, "requiredLevel": "BLOCKING", "defaultable": false, "aliases": ["姓名", "name"]},
                    {"key": "purpose", "label": "使用场景", "required": true, "requiredLevel": "SOFT", "defaultable": true, "aliases": ["purpose", "用途", "场景"]},
                    {"key": "style", "label": "风格", "required": true, "requiredLevel": "SOFT", "defaultable": true, "aliases": ["style", "风格"]}
                  ]
                }
                """;

        var completion = policy.assess(
                "请补充姓名、使用场景和风格。",
                "低风险生成任务缺少偏好字段",
                contract,
                new PendingInteractionFacts(
                        Map.of("userAcceptedDefaults", "true"),
                        java.util.List.of("purpose", "style"),
                        "用户说就这些，剩余按默认处理"),
                Map.of("name", "冯建松"));

        assertThat(completion.complete()).isTrue();
        assertThat(completion.missingFields()).isEmpty();
    }

    @Test
    void defaultConsentCanSatisfyOverStrictResumeQualitySlots() {
        String contract = """
                {
                  "slots": [
                    {"key": "full_name", "label": "姓名", "required": true, "defaultable": false, "requiredLevel": "BLOCKING"},
                    {"key": "target_position", "label": "目标职位或行业", "required": true, "defaultable": true, "requiredLevel": "SOFT", "aliases": ["应聘职位", "求职意向"]},
                    {"key": "work_experience", "label": "工作经历", "required": true, "defaultable": false, "requiredLevel": "BLOCKING", "aliases": ["工作履历"]},
                    {"key": "education", "label": "教育背景", "required": true, "defaultable": false, "requiredLevel": "BLOCKING", "aliases": ["学历", "毕业院校"]},
                    {"key": "contact_info", "label": "联系方式", "required": true, "defaultable": true, "requiredLevel": "SOFT", "aliases": ["电话", "邮箱"]}
                  ]
                }
                """;

        var completion = policy.assess(
                "为了帮您生成一份合适的简历，请先告诉我姓名、目标职位、联系方式、工作经历和教育背景。",
                "生成个人简历缺少信息",
                contract,
                new PendingInteractionFacts(
                        Map.of("userAcceptedDefaults", "true"),
                        java.util.List.of("work_experience", "education"),
                        "用户说随意，允许默认处理"),
                Map.of("full_name", "冯佳松"));

        assertThat(completion.complete()).isTrue();
        assertThat(completion.missingFields()).isEmpty();
    }

    @Test
    void mapsModelGeneratedContractSlotsToStableFactAliases() {
        String contract = """
                {
                  "version": "v1",
                  "slots": [
                    {"key": "identity", "label": "身份背景", "required": true, "defaultable": true, "aliases": ["profile"]},
                    {"key": "context", "label": "使用场合", "required": true, "defaultable": true, "aliases": ["scenario"]},
                    {"key": "tone", "label": "表达风格", "required": true, "defaultable": true, "aliases": ["voice"]}
                  ]
                }
                """;

        var completion = policy.assess(
                "自然问题",
                "阻塞摘要",
                contract,
                new PendingInteractionFacts(
                        Map.of(
                                "role", "Java 后端自由职业者",
                                "purpose", "求职面试",
                                "style", "轻松"),
                        java.util.List.of("context", "tone"),
                        "用户补充了语义等价字段"),
                Map.of());

        assertThat(completion.complete()).isTrue();
        assertThat(completion.missingFields()).isEmpty();
    }
}
