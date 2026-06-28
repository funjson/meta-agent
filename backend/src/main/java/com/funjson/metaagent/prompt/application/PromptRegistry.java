package com.funjson.metaagent.prompt.application;

import java.util.Map;

import com.funjson.metaagent.prompt.domain.PromptUseCase;
import com.funjson.metaagent.prompt.domain.RenderedPrompt;

/**
 * 提供版本化 Prompt 的加载、变量校验与渲染能力。
 */
public interface PromptRegistry {

    /**
     * 渲染指定用途的 Prompt。
     *
     * @param useCase Prompt 用途
     * @param variables 模板变量
     * @return 可发送给模型的 Prompt
     */
    RenderedPrompt render(PromptUseCase useCase, Map<String, String> variables);
}
