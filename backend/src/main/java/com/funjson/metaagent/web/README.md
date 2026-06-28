# Web Adapter

## 职责与非职责

- 负责 HTTP 路径、输入校验、状态码和响应组装。
- 不拥有领域状态，不因为 URL 包含 Conversation 就取得 Conversation 或 Control 所有权。

## 路由关系

- `/conversations` → ConversationService。
- `/conversations/{id}/messages` → ControlKernel。
- `/conversations/{id}/agent-path` → Observability AgentPathQuery。
- `/jobs`、`/task-graph-templates` → Job 应用服务。
- `/task-runs`、`/task-runs/{id}/recovery` → Task 查询与 Recovery 应用服务。
- `/authorization-requests`、`/settings/providers` → Runtime 与 Provider 应用服务。
- `/skill-packages`、`/subagent-profiles`、`/system` → 对应配置或系统应用服务。

## 所有权与依赖

所有 `@RestController` 物理上统一位于 `web.api`。Web Adapter 可以组合应用服务，
但不得持有 Conversation、ControlTurn、Job、Task 或 Loop 的领域状态。
