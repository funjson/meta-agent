package com.funjson.metaagent.runtime.domain;

/**
 * 跨层引用一个不可变 TaskGraphTemplate 版本。
 *
 * @param templateKey Profile 内稳定模板 Key
 * @param version 模板版本；为空表示由 Job 层选择当前激活版本
 */
public record TaskGraphTemplateRef(
        String templateKey,
        Integer version) {

    /**
     * 校验模板引用。
     */
    public TaskGraphTemplateRef {
        if (templateKey == null || templateKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Task graph template key is required");
        }
        if (version != null && version < 1) {
            throw new IllegalArgumentException(
                    "Task graph template version must be positive");
        }
    }
}
