package com.funjson.metaagent.provider.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 提供 Provider 配置的 MyBatis-Plus CRUD 与乐观锁更新。
 */
@Mapper
public interface ProviderConfigMapper extends BaseMapper<ProviderConfigDataObject> {

    /**
     * 使用显式期望版本更新 Provider 配置。
     *
     * @param id Provider ID
     * @param baseUrl Base URL
     * @param modelName 模型名
     * @param secretSource 密钥来源
     * @param enabled 是否启用
     * @param expectedVersion 期望版本
     * @return 受影响行数
     */
    @Update("""
            UPDATE provider_config
            SET base_url = #{baseUrl},
                model_name = #{modelName},
                secret_source = #{secretSource},
                enabled = #{enabled},
                version = version + 1
            WHERE id = #{id}
              AND version = #{expectedVersion}
            """)
    int updateWithExpectedVersion(
            @Param("id") String id,
            @Param("baseUrl") String baseUrl,
            @Param("modelName") String modelName,
            @Param("secretSource") String secretSource,
            @Param("enabled") boolean enabled,
            @Param("expectedVersion") long expectedVersion);
}
