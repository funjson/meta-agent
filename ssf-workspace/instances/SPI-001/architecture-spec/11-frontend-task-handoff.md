# 11 Frontend Task Handoff

## 必须实现

- Chat Workspace 的对话、任务状态和阶段反馈。
- 根 Job 列表与 Job Detail。
- TaskGraph 与 TaskRun 状态。
- Agent Path 中按 origin LoopNode 嵌套 Child Job。
- Template Management 与 Authorization Inbox。
- Provider Secret 配置和连接测试。

## 约束

- 不从自然语言结果猜测状态或下一步。
- 不展示隐藏思维链、完整 Prompt、Secret 和未脱敏请求。
- 所有状态、层级和关系来自后端稳定 DTO。
- 长任务通过事件流增量更新，支持断线补齐。

