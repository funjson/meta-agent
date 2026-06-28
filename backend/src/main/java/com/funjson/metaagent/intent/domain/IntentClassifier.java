package com.funjson.metaagent.intent.domain;

import java.util.Optional;

/**
 * 定义意图分类策略的统一端口。
 */
public interface IntentClassifier {

    /**
     * 尝试识别用户意图。
     *
     * @param request 意图识别请求
     * @return 当前策略能够确定时返回结果，否则返回空
     */
    Optional<IntentRecognition> classify(IntentRecognitionRequest request);
}
