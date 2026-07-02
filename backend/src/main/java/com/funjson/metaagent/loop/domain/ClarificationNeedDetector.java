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
        String value = normalize(content);
        boolean hardBlocking = value.matches(".*(缺少关键|信息缺失|无法继续|无法生成|不能完成|暂时不能|还不能|光有.*不够|不太够|还不够).*")
                || value.matches(".*(必须|需要先|只有.*才能|否则无法).*(确认|了解|知道|补充|提供).*");
        boolean optionalFollowUp = value.matches(".*(如果|如需|若|要是|假如).{0,24}(还需要|需要|想|希望|准备).{0,32}(请|可以|随时|告诉我|提供).*")
                || value.matches(".*(如果|如需|若).{0,24}(进一步|继续|调整|完善).*");
        boolean substantialAnswer = looksLikeSubstantialAnswer(content);

        // 已经给出主体结果时，末尾“如果还需要……”通常只是开放式追问，
        // 不能把整段用户可见结果升级成 WAITING_HUMAN。
        if (substantialAnswer && optionalFollowUp && !hardBlocking) {
            return false;
        }

        // 对工具型任务尤其重要：天气、搜索、文件读取等结果一旦已经形成
        // 明确数据/结论，就交给完成策略判断质量，而不是误转澄清状态。
        if (substantialAnswer && !hardBlocking) {
            return false;
        }

        boolean explicitMissing = hardBlocking
                || value.matches(".*(还需要|我还需要|需要你)(.*)(确认|了解|知道|补充|提供).*");
        boolean directAsk = asksForRequiredInput(value);
        boolean hasQuestionShape = hasQuestionShape(value);
        boolean claimsNotComplete = value.matches(".*(无法生成|不能完成|暂时不能|还不能|需要先).*");
        return (explicitMissing || directAsk)
                && (hasQuestionShape || claimsNotComplete);
    }

    /**
     * 统一文本归一化，避免换行、列表和中文空格影响语义护栏。
     *
     * @param content 原始模型输出
     * @return 适合正则判断的一行文本
     */
    private String normalize(String content) {
        return content.replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 判断模型输出是否已经包含主体答案或工具结果。
     *
     * <p>这不是完成验收，只是防止澄清检测器误吞最终回答。真正是否完成仍由
     * LoopCompletionPolicy 和模型 Judge 判断。</p>
     *
     * @param content 模型输出
     * @return 是否像一个已经形成的用户可见结果
     */
    private boolean looksLikeSubstantialAnswer(String content) {
        String value = normalize(content);
        String compact = content.replaceAll("\\s+", "");
        boolean answerIntro = value.matches(".*(以下是|下面是|为你准备|为您准备|已为你|已为您|查询结果|结果如下|总结如下|结论).*");
        boolean weatherResult = value.matches(".*(天气|气温|温度|降水|湿度|风力|空气质量|紫外线|体感温度).*")
                && value.matches(".*(今天|当前|实时|预报|北京|上海|广州|深圳).*")
                && !value.matches(".*(哪个|哪一个|什么).{0,8}(城市|地区|地点|位置).*");
        boolean tableOrData = content.contains("|")
                || compact.matches(".*(℃|°c|°C|mm|km/h|%|日期|最高|最低).*");
        return answerIntro || weatherResult || tableOrData;
    }

    /**
     * 判断文本是否在直接索取会阻塞任务的输入。
     *
     * @param value 归一化后的模型输出
     * @return 是否是关键输入追问
     */
    private boolean asksForRequiredInput(String value) {
        String requiredInputKeywords = "(姓名|角色|用途|场景|风格|长度|目标|边界|输入|输出|预算|权限|背景|岗位|行业|经验|城市|地区|地点|位置|文件|路径|日期|时间|范围|条件)";
        return value.matches(".*请.*(补充|提供).*" + requiredInputKeywords + ".*")
                || value.matches(".*请.*告诉我.*" + requiredInputKeywords + ".*")
                || value.matches(".*需要.*(补充|提供).*" + requiredInputKeywords + ".*");
    }

    /**
     * 判断文本是否具备追问形态。
     *
     * @param value 归一化后的模型输出
     * @return 是否像问题或字段清单
     */
    private boolean hasQuestionShape(String value) {
        return value.contains("？")
                || value.contains("?")
                || value.matches(".*(姓名|角色|用途|场景|风格|长度|目标|边界|输入|输出|预算|权限|背景|岗位|行业|经验|城市|地区|地点|位置|文件|路径|日期|时间|范围|条件).*");
    }
}
