package com.funjson.metaagent.context.application;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.funjson.metaagent.clarification.domain.ClarificationRequest;
import com.funjson.metaagent.context.domain.ContextBlock;
import com.funjson.metaagent.context.domain.ContextBlockType;
import com.funjson.metaagent.context.domain.ContextEnvelope;
import com.funjson.metaagent.context.domain.ContextFact;
import com.funjson.metaagent.context.domain.ContextMessage;
import com.funjson.metaagent.runtime.domain.TaskIntentScope;
import org.springframework.stereotype.Service;

/**
 * Projects Conversation-wide context into the current task intent scope.
 *
 * <p>Visible chat messages remain available so the model can understand the
 * conversation. Structured system facts and pending interactions are narrower:
 * Job-scoped facts only flow to that Job, while stable user facts such as a
 * name can be reused across tasks. This prevents mixed-turn sibling tasks from
 * treating each other's parameters as their own contract inputs.</p>
 */
@Service
public class TaskScopedContextProjector {

    /**
     * Builds context blocks relevant to one Job/Loop execution.
     *
     * @param envelope conversation context envelope
     * @param jobId current Job ID
     * @param scope task intent scope
     * @return projected context blocks
     */
    public List<ContextBlock> project(
            ContextEnvelope envelope,
            UUID jobId,
            TaskIntentScope scope) {
        return List.of(
                block(
                        ContextBlockType.CONVERSATION,
                        "Visible Conversation Messages",
                        renderMessages(envelope.visibleMessages())),
                block(
                        ContextBlockType.MEMORY,
                        "Task-Scoped Structured Facts",
                        renderFacts(envelope.conversationFacts(), jobId, scope)),
                block(
                        ContextBlockType.PENDING_INTERACTION,
                        "Task-Scoped Waiting Interactions",
                        renderClarifications(
                                envelope.openClarifications(),
                                jobId)),
                block(
                        ContextBlockType.MEMORY,
                        "Task-Scoped Resolved Clarification Facts",
                        renderClarifications(
                                envelope.resolvedClarifications(),
                                jobId)));
    }

    /**
     * Renders visible conversation messages.
     *
     * @param messages visible messages from the conversation envelope
     * @return prompt-ready message block
     */
    private String renderMessages(List<ContextMessage> messages) {
        if (messages.isEmpty()) {
            return "No visible conversation messages.";
        }
        return messages.stream()
                .map(message -> "- [%s/%s] %s".formatted(
                        message.role(),
                        message.messageType(),
                        oneLine(message.content())))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No visible conversation messages.");
    }

    /**
     * Renders facts relevant to the current task scope.
     *
     * @param facts known structured facts
     * @param jobId current Job ID
     * @param scope current task intent scope
     * @return prompt-ready fact block
     */
    private String renderFacts(
            List<ContextFact> facts,
            UUID jobId,
            TaskIntentScope scope) {
        List<ContextFact> relevant = facts.stream()
                .filter(fact -> relevantFact(fact, jobId, scope))
                .toList();
        if (relevant.isEmpty()) {
            return "No structured facts available for the current task.";
        }
        return relevant.stream()
                .map(fact -> "- %s=%s scope=%s source=%s confidence=%.2f"
                        .formatted(
                                fact.key(),
                                oneLine(fact.value()),
                                fact.scope(),
                                fact.sourceType(),
                                fact.confidence()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No structured facts available for the current task.");
    }

    /**
     * Checks whether one fact is relevant to this task.
     *
     * @param fact candidate fact
     * @param jobId current Job ID
     * @param scope current task intent scope
     * @return true when the fact can be injected into this task
     */
    private boolean relevantFact(
            ContextFact fact,
            UUID jobId,
            TaskIntentScope scope) {
        String factScope = normalize(fact.scope());
        if (factScope.equals(normalize(jobScope(jobId)))) {
            return true;
        }
        if (!"conversation".equals(factScope)) {
            return false;
        }
        String key = normalize(fact.key());
        return stableUserFact(key) || taskFact(key, scope);
    }

    /**
     * Checks whether a fact is a stable cross-task user fact.
     *
     * @param key normalized fact key
     * @return true for facts safe to share across tasks
     */
    private boolean stableUserFact(String key) {
        return key.equals("name")
                || key.equals("username")
                || key.equals("preferredname")
                || key.equals("nickname");
    }

    /**
     * Checks whether a conversation-level fact matches the current task type.
     *
     * @param key normalized fact key
     * @param scope current task intent scope
     * @return true when the key is relevant to the task type
     */
    private boolean taskFact(String key, TaskIntentScope scope) {
        String taskType = scope == null ? "" : scope.normalizedTaskType();
        if ("WEATHER_QUERY".equals(taskType)) {
            return key.equals("location")
                    || key.equals("city")
                    || key.equals("region")
                    || key.equals("place");
        }
        if ("RESUME_OR_PROFILE_GENERATION".equals(taskType)
                || "TEXT_GENERATION".equals(taskType)) {
            return key.equals("purpose")
                    || key.equals("background")
                    || key.equals("role")
                    || key.equals("occupation")
                    || key.equals("experience")
                    || key.equals("style")
                    || key.equals("length")
                    || key.equals("requirements")
                    || key.equals("outputformat");
        }
        return false;
    }

    /**
     * Renders clarification rows for the current Job only.
     *
     * @param clarifications candidate clarification requests
     * @param jobId current Job ID
     * @return prompt-ready clarification block
     */
    private String renderClarifications(
            List<ClarificationRequest> clarifications,
            UUID jobId) {
        List<ClarificationRequest> relevant = clarifications.stream()
                .filter(request -> request.jobId() != null
                        && request.jobId().equals(jobId))
                .toList();
        if (relevant.isEmpty()) {
            return "No waiting interactions for the current task.";
        }
        return relevant.stream()
                .map(request -> "- id=%s job=%s source=%s question=%s answer=%s resolution=%s"
                        .formatted(
                                request.id(),
                                request.jobId(),
                                request.sourceType(),
                                oneLine(request.question()),
                                oneLine(request.answer()),
                                oneLine(request.resolutionJson())))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No waiting interactions for the current task.");
    }

    /**
     * Creates one context block.
     *
     * @param type context block type
     * @param title context block title
     * @param content context block content
     * @return immutable context block
     */
    private ContextBlock block(
            ContextBlockType type,
            String title,
            String content) {
        return new ContextBlock(
                type,
                title,
                content,
                estimateTokens(content));
    }

    /**
     * Creates the durable fact scope string for a Job.
     *
     * @param jobId Job ID
     * @return scope string persisted in conversation_fact
     */
    private String jobScope(UUID jobId) {
        return jobId == null ? "" : "JOB:" + jobId;
    }

    /**
     * Normalizes text for key comparisons.
     *
     * @param value raw value
     * @return comparison key
     */
    private String normalize(String value) {
        return value == null
                ? ""
                : value.replaceAll("[_\\-\\s]+", "")
                        .trim()
                        .toLowerCase(Locale.ROOT);
    }

    /**
     * Renders a compact single-line value.
     *
     * @param value raw text
     * @return safe one-line text
     */
    private String oneLine(String value) {
        String normalized = value == null
                ? ""
                : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500
                ? normalized
                : normalized.substring(0, 497) + "...";
    }

    /**
     * Estimates token cost for observability.
     *
     * @param content context content
     * @return rough token estimate
     */
    private int estimateTokens(String content) {
        return Math.max(1, content == null ? 0 : content.length() / 3);
    }
}
