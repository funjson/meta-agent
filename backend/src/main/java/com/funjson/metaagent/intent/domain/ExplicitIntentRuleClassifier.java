package com.funjson.metaagent.intent.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;


/**
 * 只处理高确定性的寒暄和显式控制命令。
 *
 * <p>该分类器刻意不做关键词式任务理解。无法高置信度判断时返回空，由模型分类器
 * 继续处理，避免规则代码逐渐退化成不可维护的自然语言解析器。</p>
 */
public class ExplicitIntentRuleClassifier implements IntentClassifier {

    /**
     * 识别寒暄或显式暂停、恢复、取消和状态查询命令。
     *
     * @param request 意图识别请求
     * @return 高确定性规则命中结果
     */
    @Override
    public Optional<IntentRecognition> classify(IntentRecognitionRequest request) {
        String normalized = normalize(request.userMessage());
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (isGreeting(lower)) {
            return Optional.of(conversation(normalized));
        }
        if (isExactCommand(lower, "暂停", "暂停任务", "pause", "stop for now")) {
            return Optional.of(controlCommand(
                    IntentType.PAUSE_JOB,
                    normalized,
                    request.activeJobId() == null));
        }
        if (isExactCommand(lower, "恢复", "继续任务", "resume", "continue")) {
            return Optional.of(controlCommand(
                    IntentType.RESUME_JOB,
                    normalized,
                    request.activeJobId() == null));
        }
        if (isExactCommand(lower, "取消", "取消任务", "cancel")) {
            return Optional.of(controlCommand(
                    IntentType.CANCEL_JOB,
                    normalized,
                    request.activeJobId() == null));
        }
        if (isExactCommand(lower, "状态", "任务状态", "进度", "status", "progress")) {
            return Optional.of(controlCommand(
                    IntentType.QUERY_STATUS,
                    normalized,
                    request.activeJobId() == null));
        }
        return Optional.empty();
    }

    /**
     * 创建普通对话识别结果。
     *
     * @param content 用户消息
     * @return 普通对话结果
     */
    private IntentRecognition conversation(String content) {
        return new IntentRecognition(
                IntentType.CHAT_QA,
                0.99,
                "EXPLICIT_RULE",
                content,
                "高确定性寒暄规则命中，创建单节点 Job 交由 Loop 生成最终回复。",
                List.of(),
                false,
                false,
                IntentRiskLevel.LOW,
                List.of("chat-qa"));
    }

    /**
     * 创建显式控制命令结果。
     *
     * @param intentType 控制命令类型
     * @param content 原始命令
     * @param requiresClarification 当前是否缺少活跃 Job
     * @return 控制命令结果
     */
    private IntentRecognition controlCommand(
            IntentType intentType,
            String content,
            boolean requiresClarification) {
        return new IntentRecognition(
                intentType,
                0.98,
                "EXPLICIT_RULE",
                content,
                "识别到高确定性的显式任务控制命令。",
                List.of(),
                requiresClarification,
                false,
                IntentRiskLevel.MEDIUM,
                List.of("control-command"));
    }

    /**
     * 判断文本是否属于短寒暄。
     *
     * @param value 规范化小写文本
     * @return 是否为寒暄
     */
    private boolean isGreeting(String value) {
        return value.length() <= 16
                && (value.matches("^(你好|您好|嗨|在吗|hello|hi|hey)[！!。,. ]*$")
                || value.matches("^(谢谢|thanks|thank you)[！!。,. ]*$"));
    }

    /**
     * 判断文本是否与允许的显式命令完全匹配。
     *
     * @param value 规范化文本
     * @param commands 允许命令
     * @return 是否匹配
     */
    private boolean isExactCommand(String value, String... commands) {
        for (String command : commands) {
            if (value.equals(command)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 规范化用户输入。
     *
     * @param value 原始文本
     * @return 单行文本
     */
    private String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
