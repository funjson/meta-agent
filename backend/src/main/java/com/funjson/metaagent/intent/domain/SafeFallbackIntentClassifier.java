package com.funjson.metaagent.intent.domain;

import java.util.List;
import java.util.Optional;


/**
 * 在模型不可用时提供保守、可审计的降级分类。
 *
 * <p>降级策略不会假装完成自然语言理解：除显式规则外，实质性消息统一按创建任务
 * 处理，并使用较低置信度标记，供后续 Control Policy 决定是否澄清。</p>
 */
public class SafeFallbackIntentClassifier implements IntentClassifier {

    /**
     * 生成低置信度 CREATE_JOB 结果。
     *
     * @param request 意图识别请求
     * @return 保守降级结果
     */
    @Override
    public Optional<IntentRecognition> classify(IntentRecognitionRequest request) {
        String normalized = request.userMessage().replaceAll("\\s+", " ").trim();
        String summary = normalized.length() <= 180
                ? normalized
                : normalized.substring(0, 177) + "...";
        return Optional.of(new IntentRecognition(
                IntentType.CREATE_JOB,
                0.55,
                "SAFE_FALLBACK",
                summary,
                "模型意图分类不可用；按保守策略创建 Job，约束由后续规划阶段补充。",
                List.of("完成必须提供可验证 Evidence"),
                false,
                false,
                IntentRiskLevel.MEDIUM,
                List.of("general")));
    }
}
