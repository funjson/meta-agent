package com.funjson.metaagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Meta Agent 模块化单体的 Spring Boot 启动入口。
 */
@SpringBootApplication
@EnableScheduling
public class MetaAgentApplication {

    /**
     * 启动本地 Agent 平台。
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MetaAgentApplication.class, args);
    }
}
