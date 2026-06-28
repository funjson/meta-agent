package com.funjson.metaagent.intent.application;

import com.funjson.metaagent.intent.domain.ExplicitIntentRuleClassifier;
import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.intent.domain.IntentRecognitionRequest;
import com.funjson.metaagent.intent.domain.SafeFallbackIntentClassifier;
import com.funjson.metaagent.intent.application.port.out.ModelIntentClassifierPort;
import org.springframework.stereotype.Service;

/**
 * 编排规则、模型与安全降级三层意图识别策略。
 */
@Service
public class IntentRecognitionService {

    private final ExplicitIntentRuleClassifier explicitRuleClassifier;
    private final ModelIntentClassifierPort modelIntentClassifier;
    private final SafeFallbackIntentClassifier fallbackIntentClassifier;

    /**
     * 创建 Control Intent Pipeline。
     *
     * @param explicitRuleClassifier 高确定性规则分类器
     * @param modelIntentClassifier 模型分类器
     * @param fallbackIntentClassifier 安全降级分类器
     */
    public IntentRecognitionService(
            ExplicitIntentRuleClassifier explicitRuleClassifier,
            ModelIntentClassifierPort modelIntentClassifier,
            SafeFallbackIntentClassifier fallbackIntentClassifier) {
        this.explicitRuleClassifier = explicitRuleClassifier;
        this.modelIntentClassifier = modelIntentClassifier;
        this.fallbackIntentClassifier = fallbackIntentClassifier;
    }

    /**
     * 按固定优先级识别意图。
     *
     * @param request 意图识别请求
     * @return 已通过基础合同校验的意图
     */
    public IntentRecognition recognize(IntentRecognitionRequest request) {
        IntentRecognition recognition = explicitRuleClassifier.classify(request)
                .or(() -> modelIntentClassifier.classify(request))
                .or(() -> fallbackIntentClassifier.classify(request))
                .orElseThrow();
        return validate(recognition);
    }

    /**
     * 对所有分类器的输出应用统一不变量。
     *
     * @param recognition 原始识别结果
     * @return 校验后的结果
     */
    private IntentRecognition validate(IntentRecognition recognition) {
        if (recognition.confidence() < 0 || recognition.confidence() > 1) {
            throw new IllegalArgumentException(
                    "Intent confidence must be between 0 and 1");
        }
        if (recognition.goalSummary() == null
                || recognition.decisionSummary() == null) {
            throw new IllegalArgumentException(
                    "Intent result must contain auditable summaries");
        }
        return recognition;
    }
}
