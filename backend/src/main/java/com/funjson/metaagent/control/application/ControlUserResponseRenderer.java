package com.funjson.metaagent.control.application;

import com.funjson.metaagent.intent.domain.IntentType;
import org.springframework.stereotype.Service;

/**
 * Renders Control-layer user-visible responses when no Job is created.
 *
 * <p>Control decisions are audit objects. This renderer is the explicit bridge
 * from an internal decision to a chat-room message, preventing raw decision
 * summaries from accidentally leaking internal state.</p>
 */
@Service
public class ControlUserResponseRenderer {

    /**
     * Selects the conversation message type for a Control-only response.
     *
     * @param intentType current intent
     * @return conversation message type
     */
    public String messageType(IntentType intentType) {
        if (intentType == IntentType.PENDING_INTERACTION_HELP) {
            return "CLARIFICATION_QUESTION";
        }
        return isControlCommand(intentType)
                ? "CONTROL_COMMAND_ACK"
                : "CLARIFICATION_DISAMBIGUATION";
    }

    /**
     * Renders a user-visible message for a Control-only response.
     *
     * @param intentType current intent
     * @param decisionSummary audited decision summary
     * @return user-facing chat message
     */
    public String messageText(
            IntentType intentType,
            String decisionSummary) {
        if (intentType == IntentType.PENDING_INTERACTION_AMBIGUOUS
                || intentType == IntentType.PENDING_INTERACTION_HELP
                || intentType == IntentType.CLARIFICATION_ANSWER) {
            return decisionSummary;
        }
        if (isControlCommand(intentType)) {
            return "控制命令已记录，但对应的暂停、恢复、取消或状态查询动作"
                    + "还需要后续接入正式命令处理器。";
        }
        return "我已经收到这条消息，但当前没有匹配到可执行任务或等待交互。"
                + "请重新描述目标，或明确说明要继续哪个任务。";
    }

    /**
     * Determines whether an intent is a formal control command.
     *
     * @param intentType current intent
     * @return true when the intent is command-like
     */
    private boolean isControlCommand(IntentType intentType) {
        return intentType == IntentType.PAUSE_JOB
                || intentType == IntentType.RESUME_JOB
                || intentType == IntentType.CANCEL_JOB
                || intentType == IntentType.QUERY_STATUS;
    }
}
