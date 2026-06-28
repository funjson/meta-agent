package com.funjson.metaagent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.funjson.metaagent.loop.domain.ClarificationNeedDetector;
import org.junit.jupiter.api.Test;

/**
 * 验证模型追问关键输入时会被升级为正式澄清请求。
 */
class ClarificationNeedDetectorTest {

    private final ClarificationNeedDetector detector =
            new ClarificationNeedDetector();

    @Test
    void detectsNaturalFollowUpForMissingProfileFacts() {
        String content = """
                你好，冯建松。

                要为你生成一份合适的个人介绍，光有名字还不够，
                我还需要了解几个关键点。请你补充背景、使用场景、
                希望突出的方向、风格与长度。
                """;

        assertThat(detector.requiresClarification(content)).isTrue();
    }

    @Test
    void doesNotDetectCompletedUserFacingAnswer() {
        String content = "你好呀！有什么我可以帮你的吗？";

        assertThat(detector.requiresClarification(content)).isFalse();
    }

    @Test
    void doesNotTreatCompletedDraftWithOptionalFollowUpAsClarification() {
        String content = """
                您好，冯建松。以下是为您准备的求职自我介绍，已按照正式风格、
                约100字的要求撰写：

                本人冯建松，拥有十年软件开发经验，具备扎实的技术功底与丰富的项目实践。
                如果需要根据具体岗位方向进一步调整，请随时告诉我。
                """;

        assertThat(detector.requiresClarification(content)).isFalse();
    }
}
