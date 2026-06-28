package com.funjson.metaagent.runtime.domain;

import java.util.Set;

/**
 * 跨层传递的不可变有效权限包络。
 *
 * @param allowedProviders 允许的 Provider
 * @param allowedTools 允许的 Tool
 * @param dataScopes 允许的数据作用域
 * @param fileScopes 允许的文件作用域
 * @param delegableCapabilities 可由用户审批后委托的能力
 */
public record AuthorityEnvelope(
        Set<String> allowedProviders,
        Set<String> allowedTools,
        Set<String> dataScopes,
        Set<String> fileScopes,
        Set<String> delegableCapabilities) {

    /**
     * 复制权限集合。
     */
    public AuthorityEnvelope {
        allowedProviders = copy(allowedProviders);
        allowedTools = copy(allowedTools);
        dataScopes = copy(dataScopes);
        fileScopes = copy(fileScopes);
        delegableCapabilities = copy(delegableCapabilities);
    }

    /**
     * 计算只能收窄的权限交集。
     *
     * @param requested 下层请求包络
     * @return 有效权限
     */
    public AuthorityEnvelope narrow(AuthorityEnvelope requested) {
        return new AuthorityEnvelope(
                intersection(allowedProviders, requested.allowedProviders),
                intersection(allowedTools, requested.allowedTools),
                intersection(dataScopes, requested.dataScopes),
                intersection(fileScopes, requested.fileScopes),
                intersection(
                        delegableCapabilities,
                        requested.delegableCapabilities));
    }

    /** 复制可空集合。 */
    private static Set<String> copy(Set<String> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    /** 计算集合交集。 */
    private static Set<String> intersection(
            Set<String> left,
            Set<String> right) {
        return left.stream()
                .filter(right::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
