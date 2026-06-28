package com.funjson.metaagent.provider.infrastructure.persistence.mybatis;

import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.funjson.metaagent.provider.application.port.out.ProviderConfigStore;
import org.springframework.stereotype.Repository;

/**
 * 将 Provider 配置持久化数据转换为应用层可使用的只读记录。
 */
@Repository
public class ProviderConfigRepository implements ProviderConfigStore {

    private final ProviderConfigMapper mapper;

    /**
     * 创建 Provider 配置 Repository Adapter。
     *
     * @param mapper MyBatis-Plus Mapper
     */
    public ProviderConfigRepository(ProviderConfigMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询指定 Provider。
     *
     * @param id Provider ID
     * @return Provider 配置
     */
    public ProviderConfig find(String id) {
        ProviderConfigDataObject dataObject = mapper.selectById(id);
        if (dataObject == null) {
            throw new IllegalArgumentException("Unknown provider: " + id);
        }
        return toRecord(dataObject);
    }

    /**
     * 查询全部 Provider 配置。
     *
     * @return 按展示名称排序的配置
     */
    public List<ProviderConfig> findAll() {
        return mapper.selectList(Wrappers.<ProviderConfigDataObject>lambdaQuery()
                        .orderByAsc(ProviderConfigDataObject::getDisplayName))
                .stream()
                .map(this::toRecord)
                .toList();
    }

    /**
     * 使用乐观锁更新 Provider 配置。
     *
     * @param id Provider ID
     * @param baseUrl Base URL
     * @param modelName 模型名
     * @param secretSource 密钥来源
     * @param enabled 是否启用
     * @param expectedVersion 期望版本
     * @return 是否成功更新一行
     */
    public boolean update(
            String id,
            String baseUrl,
            String modelName,
            String secretSource,
            boolean enabled,
            long expectedVersion) {
        return mapper.updateWithExpectedVersion(
                id,
                baseUrl,
                modelName,
                secretSource,
                enabled,
                expectedVersion) == 1;
    }

    /**
     * 将数据库对象转换为不可变记录。
     *
     * @param dataObject 数据库对象
     * @return 配置记录
     */
    private ProviderConfig toRecord(ProviderConfigDataObject dataObject) {
        return new ProviderConfig(
                dataObject.getId(),
                dataObject.getProviderType(),
                dataObject.getDisplayName(),
                dataObject.getBaseUrl(),
                dataObject.getModelName(),
                dataObject.getSecretSource(),
                Boolean.TRUE.equals(dataObject.getEnabled()),
                dataObject.getVersion());
    }

}
