package com.funjson.metaagent.provider.infrastructure.fake;

import java.time.Duration;
import java.util.UUID;

import com.funjson.metaagent.provider.domain.ModelProvider;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.provider.domain.ModelResponse;
import com.funjson.metaagent.provider.infrastructure.persistence.mybatis.ModelCallRepository;
import org.springframework.stereotype.Component;

/**
 * 用于离线测试和确定性回归的 Fake Provider。
 */
@Component
public class FakeModelProvider implements ModelProvider {

    private final ModelCallRepository modelCallRepository;

    /**
     * 创建 Fake Provider。
     *
     * @param modelCallRepository 模型调用审计 Repository
     */
    public FakeModelProvider(ModelCallRepository modelCallRepository) {
        this.modelCallRepository = modelCallRepository;
    }

    @Override
    public String providerId() {
        return "fake";
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        long started = System.nanoTime();
        String normalizedGoal = request.inputSummary().replaceAll("\\s+", " ").trim();
        String content = contentFor(normalizedGoal);
        ModelResponse response = new ModelResponse(
                providerId(),
                "fake-deterministic-v1",
                content,
                "STOP");
        recordCall(request, response, started);
        return response;
    }

    /**
     * 生成面向用户的确定性回复。
     *
     * @param normalizedGoal 归一化目标
     * @return 回复内容
     */
    private String contentFor(String normalizedGoal) {
        if (normalizedGoal.matches("^(你好|您好|嗨|hello|hi|hey)[呀啊。.！! ]*$")) {
            return "你好呀，我在。你可以直接告诉我想完成什么，我会继续帮你推进。";
        }
        return """
                我已经收到你的目标：%s

                这是一次离线确定性回复，用于验证聊天、任务执行和结果回传链路。真实模型启用后，我会根据上下文生成更具体的结果。
                """.formatted(normalizedGoal).trim();
    }

    /**
     * 保存与真实 Provider 相同结构的模型调用审计。
     *
     * @param request 模型请求
     * @param response 模型响应
     * @param started 开始纳秒
     */
    private void recordCall(
            ModelRequest request,
            ModelResponse response,
            long started) {
        modelCallRepository.insert(
                UUID.randomUUID(),
                request.taskRunId(),
                request.loopNodeId(),
                providerId(),
                response.model(),
                request.prompt().contentHash(),
                request.prompt().promptId(),
                request.prompt().version(),
                request.prompt().contentHash(),
                "COMPLETED",
                null,
                null,
                Duration.ofNanos(System.nanoTime() - started).toMillis(),
                null);
    }
}
