package com.funjson.metaagent.provider.api;

import java.util.List;

import com.funjson.metaagent.provider.domain.ModelCapabilities;

/**
 * 前端模型选择使用的模型能力视图。
 *
 * @param id 框架模型 ID
 * @param displayName 展示名
 * @param providerId Provider ID
 * @param providerModel 厂商模型名
 * @param family 模型族
 * @param contextWindow 上下文窗口
 * @param modalities 模态
 * @param capabilities 能力
 */
public record ModelSpecView(
        String id,
        String displayName,
        String providerId,
        String providerModel,
        String family,
        int contextWindow,
        List<String> modalities,
        ModelCapabilities capabilities) {
}
