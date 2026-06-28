package com.funjson.metaagent.intent.application.port.out;

import java.util.Optional;

import com.funjson.metaagent.intent.domain.PendingInteractionRoute;
import com.funjson.metaagent.intent.domain.PendingInteractionRoutingRequest;

/**
 * Pending Interaction 的模型化结构化路由端口。
 */
public interface ModelPendingInteractionRouterPort {

    /**
     * 尝试调用模型输出结构化路由决策。
     *
     * @param request 路由请求
     * @return 成功解析且通过基础校验时返回决策
     */
    Optional<PendingInteractionRoute> route(
            PendingInteractionRoutingRequest request);
}
