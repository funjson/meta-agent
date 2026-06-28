# 05 API Contracts

## Conversation、Web Adapter 与 Control

- `POST /api/v1/conversations`
- `GET /api/v1/conversations`
- `GET /api/v1/conversations/{id}`
- `POST /api/v1/conversations/{id}/messages`
- `GET /api/v1/conversations/{id}/agent-path`

消息路径属于 Conversation 资源语义，由 Web Adapter 调用 ControlKernel。聊天响应返回 Conversation、
ControlTurn ID、结构化 ControlDecision、可选根 Job 和当前可见进度，不把 Controller 归入 Conversation
或 Control 领域模块。

## Job

- `GET /api/v1/jobs`：默认只返回根 Job。
- `GET /api/v1/jobs/{id}`：返回父 Job、根 Job、递归深度、模板来源与子 Job。
- `POST /api/v1/jobs/{id}/pause`
- `POST /api/v1/jobs/{id}/resume`
- `POST /api/v1/jobs/{id}/cancel`

## TaskGraphTemplate

- `POST /api/v1/task-graph-templates`
- `GET /api/v1/task-graph-templates`
- `GET /api/v1/task-graph-templates/{id}/versions/{version}`
- `POST /api/v1/task-graph-templates/{id}/versions/{version}/activate`

模板写入前执行 Schema、稳定 key、依赖引用和 DAG 无环校验。

## Authorization

- `GET /api/v1/authorization-requests?status=PENDING`
- `POST /api/v1/authorization-requests/{id}/approve`
- `POST /api/v1/authorization-requests/{id}/reject`

## TaskRun 与事件

- `GET /api/v1/task-runs/{id}`
- `POST /api/v1/task-runs/{id}/resume`
- `GET /api/v1/task-runs/{id}/events`

所有变更接口要求幂等键；错误响应包含稳定 code、message、traceId 和可选 details。

## SkillPackage 与 SubagentProfile

- `POST /api/v1/skill-packages/import`
- `GET /api/v1/skill-packages/{id}/versions/{version}`
- `POST /api/v1/subagent-profiles`
- `GET /api/v1/subagent-profiles`

SkillPackage 导入请求包含 `SKILL.md` 与资源列表；脚本资源只注册 executable 元数据，不在导入接口执行。
SubagentProfile 创建时校验 AgentProfile、模型 allowlist、Skill 引用、Tool allowlist 和 AuthorityEnvelope。
