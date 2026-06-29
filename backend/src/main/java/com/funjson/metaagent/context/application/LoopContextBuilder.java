package com.funjson.metaagent.context.application;

import java.util.ArrayList;
import java.util.List;

import com.funjson.metaagent.capability.domain.CapabilityPlanningContext;
import com.funjson.metaagent.context.domain.ContextBlock;
import com.funjson.metaagent.context.domain.ContextBlockType;
import com.funjson.metaagent.context.domain.LoopContextSnapshot;
import com.funjson.metaagent.file.application.FileAttachmentService;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.tool.application.ToolCatalogService;
import com.funjson.metaagent.tool.application.port.out.ToolStore;
import org.springframework.stereotype.Service;

/**
 * 为 ReAct Loop 构建结构化上下文快照。
 */
@Service
public class LoopContextBuilder {

    private final ToolCatalogService toolCatalogService;
    private final ToolStore toolStore;
    private final ContextAssembler contextAssembler;
    private final FileAttachmentService fileAttachmentService;

    /**
     * 创建 Loop Context Builder。
     *
     * @param toolCatalogService Tool 目录服务
     * @param toolStore Tool Store，用于从 Job 反查 Conversation
     * @param contextAssembler 统一上下文装配器
     * @param fileAttachmentService 文件附件服务
     */
    public LoopContextBuilder(
            ToolCatalogService toolCatalogService,
            ToolStore toolStore,
            ContextAssembler contextAssembler,
            FileAttachmentService fileAttachmentService) {
        this.toolCatalogService = toolCatalogService;
        this.toolStore = toolStore;
        this.contextAssembler = contextAssembler;
        this.fileAttachmentService = fileAttachmentService;
    }

    /**
     * 构造当前 LoopNode 的模型上下文。
     *
     * @param context LoopNode 执行上下文
     * @param capabilityContext 已解析 Capability 作用域
     * @return 上下文快照
     */
    public LoopContextSnapshot build(
            RunExecutionContext context,
            CapabilityPlanningContext capabilityContext) {
        List<ContextBlock> blocks = new ArrayList<>();
        blocks.add(block(
                ContextBlockType.SYSTEM,
                "Loop Kernel",
                "你正在执行一个 ReAct 小闭环。你可以使用模型回答、工具、"
                        + "clarification.request 或派生动作；最终用户回复不得暴露内部对象名。"));
        toolStore.findConversationIdByJobId(context.jobId())
                .ifPresent(conversationId -> {
                    blocks.addAll(contextAssembler.loopConversationBlocks(
                            contextAssembler.envelope(conversationId)));
                    // 文件清单只进入结构化上下文；文件正文必须由模型显式选择 file.read 工具读取。
                    blocks.add(block(
                            ContextBlockType.FILE,
                            "Conversation Files",
                            fileAttachmentService.promptSummary(
                                    conversationId)));
                });
        blocks.add(block(
                ContextBlockType.USER_GOAL,
                "Task Goal",
                context.goal()));
        if (!context.feedback().isBlank()) {
            blocks.add(block(
                    ContextBlockType.OBSERVATION,
                    "Parent Feedback",
                    context.feedback()));
        }
        if (!capabilityContext.scopedContext()
                .instructionSummary()
                .isBlank()) {
            blocks.add(block(
                    ContextBlockType.CAPABILITY,
                    "Scoped Skill Instructions",
                    capabilityContext.scopedContext()
                            .instructionSummary()));
        }
        blocks.add(block(
                ContextBlockType.TOOL_CATALOG,
                "Available Tools",
                toolCatalogService.promptSummary()));
        blocks.add(block(
                ContextBlockType.CONTRACT,
                "Runtime Boundary And Contract",
                "provider=%s, depth=%d, iteration=%d, taskRun=%s"
                        .formatted(
                                context.providerId(),
                                context.depth(),
                                context.iterationNo(),
                                context.taskRunId())));
        return new LoopContextSnapshot(
                context.taskRunId(),
                context.loopNodeId(),
                blocks,
                contextAssembler.defaultTokenBudget());
    }

    /**
     * 创建上下文块并估算 Token。
     *
     * @param type 类型
     * @param title 标题
     * @param content 内容
     * @return 上下文块
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
     * 简单估算 Token，v0.1 用于预算观测，后续替换为模型 tokenizer。
     *
     * @param content 文本
     * @return 粗略 Token 数
     */
    private int estimateTokens(String content) {
        return Math.max(1, content == null ? 0 : content.length() / 3);
    }
}
