package com.funjson.metaagent.control.api;

import com.funjson.metaagent.conversation.api.ConversationView;
import com.funjson.metaagent.job.api.JobView;
import com.funjson.metaagent.task.api.TaskRunView;

import java.util.UUID;

/**
 * 一轮聊天在 Control 与 Loop 执行后的聚合响应。
 *
 * @param controlTurnId ControlTurn ID
 * @param conversation 最新 Conversation
 * @param controlDecision ControlDecision
 * @param job 创建或控制的 Job
 * @param taskRun 本轮 TaskRun
 */
public record ChatTurnResult(
        UUID controlTurnId,
        ConversationView conversation,
        ControlDecisionView controlDecision,
        JobView job,
        TaskRunView taskRun) {
}
