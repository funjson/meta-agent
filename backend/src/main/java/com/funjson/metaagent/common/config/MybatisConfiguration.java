package com.funjson.metaagent.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置 Meta Agent 的 MyBatis 与 MyBatis-Plus 基础能力。
 *
 * <p>分页和乐观锁属于所有持久化模块共享的技术能力，因此集中放在 common
 * 配置层，而不是散落在具体业务模块。</p>
 */
@Configuration
@MapperScan(
        basePackages = "com.funjson.metaagent",
        annotationClass = Mapper.class)
public class MybatisConfiguration {

    /**
     * 创建 MyBatis-Plus 拦截器链。
     *
     * @return 包含 MySQL 分页和乐观锁能力的拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
