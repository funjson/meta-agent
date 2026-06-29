package com.funjson.metaagent.provider.domain;

import java.util.List;

/**
 * 配置驱动的模型能力目录条目。
 *
 * @param id 框架内稳定模型 ID
 * @param displayName 展示名称
 * @param providerId 适配器 Provider ID
 * @param providerModel 厂商实际模型名
 * @param family 模型族
 * @param contextWindow 上下文窗口大小
 * @param modalities 输入/输出模态
 * @param capabilities 能力快照
 */
public record ModelSpec(
        String id,
        String displayName,
        String providerId,
        String providerModel,
        String family,
        int contextWindow,
        List<String> modalities,
        ModelCapabilities capabilities) {

    /**
     * 复制模态集合并校验关键字段。
     */
    public ModelSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Model id is required");
        }
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Provider id is required");
        }
        if (providerModel == null || providerModel.isBlank()) {
            throw new IllegalArgumentException("Provider model is required");
        }
        displayName = displayName == null || displayName.isBlank()
                ? id
                : displayName;
        family = family == null || family.isBlank() ? providerId : family;
        modalities = modalities == null ? List.of("text") : List.copyOf(modalities);
        capabilities = capabilities == null
                ? new ModelCapabilities(false, false, false, false, false)
                : capabilities;
    }
}
