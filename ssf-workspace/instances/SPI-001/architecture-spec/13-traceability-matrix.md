# 13 Traceability Matrix

## 实现校准 r7

- REQ-AF-005：Skill 脚本资源现在通过 ToolCatalog 暴露为 ToolDefinition，并可由 ToolExecutionService 受控执行。
- REQ-AF-007：中断恢复覆盖 `CLARIFICATION_ANSWERED`，用户回答后可恢复原 TaskRun / LoopNode。
- REQ-AF-010：Agent Path 增加 `TOOL_CALL` 节点，并继续展示 `CLARIFICATION_REQUEST`、Checkpoint、RecoveryAttempt。

| REQ | Module | API/UI | Data | Acceptance | Slice |
|---|---|---|---|---|---|
| REQ-AF-001 聊天入口 | web/conversation/control/intent | Chat Workspace, `/conversations/*/messages` | conversation/message/control_turn/control_decision | AC-002/016 | R1/R7 |
| REQ-AF-002 核心层级 | job/task/loop | Job Detail, TaskGraph | job/task/task_run/loop_run/loop_node | AC-001/011 | R1/R3 |
| REQ-AF-003 配置型图 | job | Template Management | task_graph_template | AC-003 | R2 |
| REQ-AF-004 策略继承 | runtime/job/task/loop | Authorization Inbox | policy snapshot/authorization_request | AC-005 | R4 |
| REQ-AF-005 Skill 编译 | capability/runtime/profile | Skill config | capability_source/load/skill_package/skill_resource/subagent_profile | AC-004/019 | R4/R7 |
| REQ-AF-006 子 Job | job/task/loop/runtime | nested Agent Path | job parent/root/job_derivation | AC-006/007 | R5 |
| REQ-AF-007 中断恢复 | task/loop/recovery | TaskRun events | task_run_dispatch/checkpoint/recovery_attempt | AC-008/010/017/018 | R5/R7 |
| REQ-AF-008 暂停取消 | job/task/recovery | Job controls | job/task_run/event | AC-009 | R5 |
| REQ-AF-009 分层验收 | job/task/loop | Job/Run result | evidence/evaluation_run | AC-011 | R3 |
| REQ-AF-010 可观测评测 | observability/evaluation | Agent Path | runtime_event/evidence/model_call/tool_call | AC-015 | R6 |
| REQ-AF-011 上下文工程 | context/intent/loop | ContextEnvelope / Prompt View | message/clarification_request/runtime_event | AC-017/018/019 | R8 |
| REQ-AF-012 前端验收辅助 | frontend/observability | Conversation History / Copy Export | conversation/message/path projection | AC-020 | R8 |
| REQ-AF-011 工程质量 | all | n/a | n/a | AC-012/013/014 | R1-R6 |
