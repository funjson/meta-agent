package com.funjson.metaagent.provider.api;

import java.util.List;

/**
 * 模型目录 API 视图。
 *
 * @param defaultModelId 默认 executor 模型
 * @param fallbackModelId 无密钥时的本地 fallback 模型
 * @param models 可选模型列表
 */
public record ModelCatalogView(
        String defaultModelId,
        String fallbackModelId,
        List<ModelSpecView> models) {
}
