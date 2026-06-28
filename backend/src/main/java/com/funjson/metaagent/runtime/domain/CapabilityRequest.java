package com.funjson.metaagent.runtime.domain;

import java.util.Set;

/**
 * 下层向上层申请的 Provider、Tool、数据或副作用能力。
 *
 * @param providers Provider ID
 * @param tools Tool ID
 * @param dataScopes 数据作用域
 * @param sideEffectClasses 副作用等级
 */
public record CapabilityRequest(
        Set<String> providers,
        Set<String> tools,
        Set<String> dataScopes,
        Set<String> sideEffectClasses) {

    /**
     * 复制集合，避免运行中修改授权请求。
     */
    public CapabilityRequest {
        providers = providers == null ? Set.of() : Set.copyOf(providers);
        tools = tools == null ? Set.of() : Set.copyOf(tools);
        dataScopes = dataScopes == null
                ? Set.of()
                : Set.copyOf(dataScopes);
        sideEffectClasses = sideEffectClasses == null
                ? Set.of()
                : Set.copyOf(sideEffectClasses);
    }

    /**
     * @return 不申请额外能力的请求
     */
    public static CapabilityRequest none() {
        return new CapabilityRequest(
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of());
    }
}
