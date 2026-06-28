package com.funjson.metaagent.loop.domain;

import java.util.Locale;

/**
 * 判断模型动作结果是否其实是在请求用户补充信息。
 *
 * <p>这是通用语义护栏：只要模型没有完成任务，而是在问用户要关键输入，
 * Loop 就必须转成正式 ClarificationRequest，不能以“非空文本”验收。</p>
 */
public class ClarificationNeedDetector {

    /**
     * 判断内容是否应升级为正式澄清请求。
     *
     * @param content 模型输出
     * @return 是否需要澄清
     */
    public boolean requiresClarification(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String value = content.replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        boolean explicitMissing = value.matches(".*(还缺少|缺少关键|信息缺失|无法继续|无法生成|不能完成|暂时不能|还不能|光有.*不够|不太够|还不够).*")
                || value.matches(".*(需要先|我需要先|还需要|我还需要|需要你)(.*)(确认|了解|知道|补充|提供).*");
        boolean directAsk = value.matches(".*请.*(补充|提供).*(姓名|角色|用途|场景|风格|长度|目标|边界|输入|输出|预算|权限|背景|岗位|行业|经验).*")
                || value.matches(".*请.*告诉我.*(姓名|角色|用途|场景|风格|长度|目标|边界|输入|输出|预算|权限|背景|岗位|行业|经验).*");
        boolean hasQuestionShape = value.contains("？")
                || value.contains("?")
                || value.matches(".*(姓名|角色|用途|场景|风格|长度|目标|边界|输入|输出|预算|权限|背景|岗位|行业|经验).*");
        boolean claimsNotComplete = value.matches(".*(无法生成|不能完成|暂时不能|还不能|需要先).*");
        return (explicitMissing || directAsk) && (hasQuestionShape || claimsNotComplete);
    }
}
