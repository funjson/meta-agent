package com.funjson.metaagent.provider.application.port.out;

import java.util.List;

/**
 * 定义 Provider 配置查询与乐观锁更新端口。
 */
public interface ProviderConfigStore {

    /** @return 指定 Provider 配置 */
    ProviderConfig find(String id);

    /** @return 全部 Provider 配置 */
    List<ProviderConfig> findAll();

    /** @return 是否成功更新 */
    boolean update(
            String id,
            String baseUrl,
            String modelName,
            String secretSource,
            boolean enabled,
            long expectedVersion);

    /**
     * Provider 配置的应用层不可变快照。
     *
     * @param id Provider ID
     * @param providerType Provider 类型
     * @param displayName 展示名称
     * @param baseUrl Base URL
     * @param modelName 模型名
     * @param secretSource 密钥来源
     * @param enabled 是否启用
     * @param version 乐观锁版本
     */
    record ProviderConfig(
            String id,
            String providerType,
            String displayName,
            String baseUrl,
            String modelName,
            String secretSource,
            boolean enabled,
            long version) {
    }
}
