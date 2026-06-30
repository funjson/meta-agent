# 13 Traceability Matrix

## 实现校准 r7

- REQ-AF-005：Skill 脚本资源现在通过 ToolCatalog 暴露为 ToolDefinition，并可由 ToolExecutionService 受控执行。
- REQ-AF-007：中断恢复覆盖 `CLARIFICATION_ANSWERED`，用户回答后可恢复原 TaskRun / LoopNode。
- REQ-AF-010：Agent Path 增加 `TOOL_CALL` 节点，并继续展示 `CLARIFICATION_REQUEST`、Checkpoint、RecoveryAttempt。
- REQ-AF-011：用户可见消息必须由显式 Renderer 输出，系统字段、结构化合同、Checkpoint 和运行节点名称不得直接进入聊天室。

## 实现校准 r12-r13

- REQ-AF-001：聊天轮次路由升级为 `TurnRoutingPlan`，支持保守混合意图。
- REQ-AF-007：`JobReplayService` 覆盖 Control 提交后 Worker 未入队的崩溃恢复点。
- REQ-AF-011：`conversation_fact` 将结构化用户事实注入上下文，而不是伪造聊天消息。
- REQ-AF-005/010：Provider native tool calling 优先连接 Tool/Skill；`ReActActionPlanner`
  作为 fallback JSON planner，并保留 Tool Observation 的路径语义。
- REQ-AF-013：Conversation 文件上传、`conversation_file`、`file.list/read/search/write`
  和前端附件入口形成第一批框架内置文件工具。
- REQ-AF-014：`web.search` 作为第一批网络搜索工具进入 ReAct Action Planning，结果进入
  ToolInvocation 与 Agent Path，再由下一轮模型合成答案。

## 实现校准 r15

- REQ-AF-005/010：`ModelRequest.tools` 与 `ModelResponse.toolCalls` 支持原生 function/tool calling，
  减少“planner 调用 + executor 调用”的双模型开销。
- REQ-AF-010/015：`LoopCorrectionPolicy` 作为长任务纠偏入口，先覆盖重复工具调用循环，后续扩展漂移评分。
- REQ-AF-004/010：`ModelThinkingMode` 与 `reasoningContent` 进入 Provider 合同，默认不展示完整推理文本。

## 实现校准 r16

- REQ-AF-010/014：`web.fetch` / `web.extract` 写入正式 Web Research 证据池，
  Agent Path 展示 `WEB_SOURCE` / `WEB_EVIDENCE`，用于复盘“读了哪些来源、抽了哪些证据”。
- REQ-AF-014：`web.search` 仍只是候选来源发现，未读取的搜索摘要不进入证据池。

## 实现校准 r17

- REQ-AF-010/014：`web.search` 写入 `web_search_run` / `web_search_candidate`，
  Agent Path 展示 `WEB_SEARCH_RUN` / `WEB_SEARCH_CANDIDATE`，用于复盘“搜索了什么、候选有哪些”。
- REQ-AF-014：`LoopContextBuilder` 将 Web Research Evidence Pool 注入后续 Loop Context，
  明确区分候选线索和已读取证据。
- REQ-AF-014/015：`LoopCorrectionPolicy` 按 Tool ID 阻断重复搜索，同时允许
  `web.search → web.fetch/web.extract → MODEL_CALL` 的研究链路。
- REQ-AF-002/014：`DefaultResearchTaskGraphFactory` 在无配置模板且命中
  `research-depth:deep-research` 时创建默认 Deep Research TaskGraph。

| REQ | Module | API/UI | Data | Acceptance | Slice |
|---|---|---|---|---|---|
| REQ-AF-001 聊天入口 | web/conversation/control/intent | Chat Workspace, `/conversations/*/messages` | conversation/message/control_turn/control_decision | AC-002/016 | R1/R7 |
| REQ-AF-002 核心层级 | job/task/loop | Job Detail, TaskGraph | job/task/task_run/loop_run/loop_node | AC-001/011 | R1/R3 |
| REQ-AF-003 配置型图 | job | Template Management | task_graph_template | AC-003 | R2 |
| REQ-AF-004 策略继承 | runtime/job/task/loop | Authorization Inbox | policy snapshot/authorization_request | AC-005 | R4 |
| REQ-AF-005 Skill 编译 | capability/runtime/profile/tool/loop | Skill config / Tool Catalog | capability_source/load/skill_package/skill_resource/subagent_profile/tool_invocation | AC-004/019 | R4/R7/R13 |
| REQ-AF-006 子 Job | job/task/loop/runtime | nested Agent Path | job parent/root/job_derivation | AC-006/007 | R5 |
| REQ-AF-007 中断恢复 | job/task/loop/recovery/control | TaskRun events / Job replay | task_run_dispatch/checkpoint/recovery_attempt/job | AC-008/010/017/018 | R5/R7/R13 |
| REQ-AF-008 暂停取消 | job/task/recovery | Job controls | job/task_run/event | AC-009 | R5 |
| REQ-AF-009 分层验收 | job/task/loop | Job/Run result | evidence/evaluation_run | AC-011 | R3 |
| REQ-AF-010 可观测评测 | observability/evaluation/loop/tool/websearch | Agent Path | runtime_event/evidence/model_call/tool_call/web_search_run/web_search_candidate/web_source_document/web_evidence_item | AC-015 | R6/R13/R16/R17 |
| REQ-AF-011 可见消息边界 | control/clarification/conversation | Chat, Agent Path | message/clarification_request/control_decision | AC-015/016 | R6 |
| REQ-AF-011 上下文工程 | context/intent/loop | ContextEnvelope / Prompt View | message/clarification_request/runtime_event/conversation_fact | AC-017/018/019 | R8/R12 |
| REQ-AF-012 前端验收辅助 | frontend/observability | Conversation History / Copy Export | conversation/message/path projection | AC-020 | R8 |
| REQ-AF-013 文件工具与上传 | file/tool/context/loop/frontend | File Upload / Tool Catalog | conversation_file/tool_invocation/runtime_event | AC-021 | R13 |
| REQ-AF-014 网络搜索工具 | websearch/tool/loop/observability/job/context | Tool Catalog / Agent Path | tool_invocation/runtime_event/web_search_run/web_search_candidate/web_source_document/web_evidence_item | AC-022 | R14/R15/R16/R17 |
| REQ-AF-015 任务纠偏与思考模式 | loop/provider/tool/observability | Agent Path / Provider Settings | model_call/tool_invocation/runtime_event | AC-023 | R15/R17 |
| REQ-AF-011 工程质量 | all | n/a | n/a | AC-012/013/014 | R1-R6 |
