package com.funjson.metaagent.provider.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 映射 {@code provider_config} 表的 MyBatis-Plus 数据对象。
 *
 * <p>该对象只存在于 infrastructure 层，不允许作为 API DTO 或领域对象向外传播。</p>
 */
@TableName("provider_config")
public class ProviderConfigDataObject {

    @TableId
    private String id;
    private String providerType;
    private String displayName;
    private String baseUrl;
    private String modelName;
    private String secretSource;
    private Boolean enabled;
    private Long version;

    /**
     * 返回 Provider ID。
     *
     * @return Provider ID
     */
    public String getId() {
        return id;
    }

    /**
     * 设置 Provider ID。
     *
     * @param id Provider ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 返回 Provider 类型。
     *
     * @return Provider 类型
     */
    public String getProviderType() {
        return providerType;
    }

    /**
     * 设置 Provider 类型。
     *
     * @param providerType Provider 类型
     */
    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    /**
     * 返回展示名称。
     *
     * @return 展示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 设置展示名称。
     *
     * @param displayName 展示名称
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 返回 Provider Base URL。
     *
     * @return Base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 设置 Provider Base URL。
     *
     * @param baseUrl Base URL
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 返回模型名。
     *
     * @return 模型名
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * 设置模型名。
     *
     * @param modelName 模型名
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    /**
     * 返回密钥来源。
     *
     * @return 密钥来源
     */
    public String getSecretSource() {
        return secretSource;
    }

    /**
     * 设置密钥来源。
     *
     * @param secretSource 密钥来源
     */
    public void setSecretSource(String secretSource) {
        this.secretSource = secretSource;
    }

    /**
     * 返回是否启用。
     *
     * @return 是否启用
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回乐观锁版本。
     *
     * @return 版本
     */
    public Long getVersion() {
        return version;
    }

    /**
     * 设置乐观锁版本。
     *
     * @param version 版本
     */
    public void setVersion(Long version) {
        this.version = version;
    }
}
