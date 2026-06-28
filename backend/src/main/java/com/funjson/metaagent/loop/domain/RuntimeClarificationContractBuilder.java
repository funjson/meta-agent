package com.funjson.metaagent.loop.domain;

import java.util.Locale;

/**
 * 为 Loop 运行时自然语言澄清生成最低限度结构化合同。
 *
 * <p>理想路径是模型或 Skill 直接调用 {@code clarification.request} 并提交结构化合同。
 * 当前 Loop 仍支持把模型自然语言追问兜底升级为正式澄清请求，因此这里需要给兜底路径补一份
 * 可审计合同，避免后续多轮补充只能依赖问题文本和 {@code missingFields} 推断。</p>
 */
public class RuntimeClarificationContractBuilder {

    /**
     * 根据任务目标和澄清问题生成合同 JSON。
     *
     * @param goal 当前 LoopNode 目标
     * @param question 模型提出的自然语言澄清问题
     * @return 合同 JSON 字符串
     */
    public String build(
            String goal,
            String question) {
        String text = normalize(goal + " " + question);
        if (containsAny(text, "简历", "履历", "求职", "面试", "resume", "cv")) {
            return resumeContract();
        }
        if (containsAny(text, "自我介绍", "个人介绍", "介绍自己", "口播", "简介")) {
            return introductionContract();
        }
        return genericContract();
    }

    /**
     * 简历类任务合同。
     *
     * <p>这些槽位会影响简历质量，但用户可以明确说“默认/随意/没有了”提前收口；
     * 此时下游模型应使用占位符或通用表达继续完成，而不是无限追问。</p>
     */
    private String resumeContract() {
        return """
                {
                  "version": "runtime-v1",
                  "slots": [
                    {"key": "name", "label": "姓名或称呼", "required": true, "defaultable": true, "aliases": ["name", "姓名", "名字", "称呼"]},
                    {"key": "role", "label": "目标岗位或角色", "required": true, "defaultable": true, "aliases": ["role", "position", "targetPosition", "jobTitle", "岗位", "职位", "求职意向", "角色"]},
                    {"key": "background", "label": "个人背景", "required": true, "defaultable": true, "aliases": ["background", "profile", "identity", "背景", "身份", "个人信息"]},
                    {"key": "experience", "label": "工作经历或年限", "required": true, "defaultable": true, "aliases": ["experience", "yearsExperience", "workExperience", "经验", "经历", "工作经验", "年限"]},
                    {"key": "educationLevel", "label": "学历或教育背景", "required": true, "defaultable": true, "aliases": ["education", "educationLevel", "degree", "学历", "教育背景", "学校", "专业"]},
                    {"key": "skills", "label": "技能特长", "required": false, "defaultable": true, "aliases": ["skills", "strengths", "技能", "特长", "能力", "证书"]},
                    {"key": "contact", "label": "联系方式", "required": false, "defaultable": true, "aliases": ["contact", "phone", "email", "联系方式", "电话", "邮箱"]},
                    {"key": "style", "label": "简历风格", "required": true, "defaultable": true, "aliases": ["style", "tone", "风格", "语气", "正式", "简洁"]},
                    {"key": "requirements", "label": "特别要求", "required": false, "defaultable": true, "aliases": ["requirements", "mustInclude", "mustAvoid", "特别要求", "避免", "突出"]}
                  ],
                  "defaultConsentPhrases": ["默认即可", "随意吧", "其他随意", "你看着办", "没有了", "按通用模板"]
                }
                """.trim();
    }

    /**
     * 自我介绍/个人介绍类任务合同。
     */
    private String introductionContract() {
        return """
                {
                  "version": "runtime-v1",
                  "slots": [
                    {"key": "name", "label": "姓名或称呼", "required": true, "defaultable": true, "aliases": ["name", "姓名", "名字", "称呼"]},
                    {"key": "purpose", "label": "使用场景", "required": true, "defaultable": true, "aliases": ["purpose", "useCase", "scenario", "用途", "场景", "场合"]},
                    {"key": "background", "label": "身份或背景", "required": true, "defaultable": true, "aliases": ["background", "role", "occupation", "experience", "背景", "身份", "职业", "岗位", "经验"]},
                    {"key": "style", "label": "风格偏好", "required": true, "defaultable": true, "aliases": ["style", "tone", "风格", "语气"]},
                    {"key": "length", "label": "长度要求", "required": true, "defaultable": true, "aliases": ["length", "wordCount", "长度", "字数", "篇幅"]},
                    {"key": "requirements", "label": "特别要求", "required": false, "defaultable": true, "aliases": ["requirements", "mustInclude", "mustAvoid", "特别要求", "避免", "突出"]}
                  ],
                  "defaultConsentPhrases": ["默认即可", "随意吧", "其他随意", "你看着办", "没有了", "按通用模板"]
                }
                """.trim();
    }

    /**
     * 通用任务澄清合同。
     */
    private String genericContract() {
        return """
                {
                  "version": "runtime-v1",
                  "slots": [
                    {"key": "goalBoundary", "label": "目标边界", "required": true, "defaultable": true, "aliases": ["goal", "scope", "boundary", "目标", "边界", "范围"]},
                    {"key": "inputScope", "label": "输入范围", "required": true, "defaultable": true, "aliases": ["input", "source", "data", "输入", "资料", "范围"]},
                    {"key": "outputExpectation", "label": "期望产物", "required": true, "defaultable": true, "aliases": ["output", "format", "deliverable", "输出", "格式", "产物"]},
                    {"key": "constraints", "label": "约束或偏好", "required": false, "defaultable": true, "aliases": ["constraints", "style", "preference", "限制", "偏好", "风格"]}
                  ],
                  "defaultConsentPhrases": ["默认即可", "随意吧", "其他随意", "你看着办", "没有了", "按通用方式"]
                }
                """.trim();
    }

    /** 判断归一化文本是否包含任意关键词。 */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(normalize(keyword))) {
                return true;
            }
        }
        return false;
    }

    /** 归一化字符串，便于中英文关键词匹配。 */
    private String normalize(String value) {
        return value == null
                ? ""
                : value.replaceAll("\\s+", "")
                        .trim()
                        .toLowerCase(Locale.ROOT);
    }
}
