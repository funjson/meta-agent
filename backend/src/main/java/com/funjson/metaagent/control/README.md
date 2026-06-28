# Control 模块

## 职责与非职责

- 负责 ControlTurn、ControlDecision、聊天轮次路由、控制命令和用户状态反馈。
- Control 是控制平面；不存在名为 `Control` 的聚合对象。
- 不拥有 Conversation/Message，不执行 TaskGraph 内部调度或 Loop 动作。

## 类图

```mermaid
classDiagram
  ConversationMessageController --> ControlKernel
  ControlKernel --> ControlTurnInitializer
  ControlKernel --> ControlJobWorker
  ControlTurnInitializer --> ControlTurnStore
  ControlTurnInitializer --> IntentRecognitionService
  ControlTurnInitializer --> PendingInteractionRouter
  ControlTurnInitializer --> ContextAssembler
  ControlTurnInitializer --> JobService
  ControlTurnInitializer --> ClarificationService
```

## 核心流程

用户 Message → 创建 ControlTurn → ContextEnvelope → PendingInteractionRouter
→ IntentRecognition → ControlDecision → 可选 Job 初始化
→ 可选 ClarificationRequest → ControlTurn 完成 → 后台 Worker 执行 Job。

Control 不再把 `JOB_ACCEPTED` 这类内部调度状态写入聊天消息。用户可见消息只包括自然回复、澄清问题、消歧问题和失败提示。

当 Router 识别为澄清回答时，Control 只负责把结构化 facts 与回答绑定回原恢复点；
是否继续执行、如何验收和如何产出最终回复仍由 Job/Task/Loop 后续阶段负责。

## 类与功能关系

- `ControlKernel`：一轮控制处理的应用门面。
- `ControlTurnInitializer`：短事务创建 Message、ControlTurn、Decision 和可选 Job。
- `ControlJobWorker`：v0.1 本机后台 Worker，异步执行 Job；完成时写回用户可见结果，WAITING_HUMAN 时写回正式澄清问题。
- `ControlTurnStore`：ControlTurn/ControlDecision 的唯一写入端口。

## 所有权和允许依赖

允许依赖 Conversation、Intent、Job、Clarification 和 Runtime。下层禁止反向依赖 Control。

## 扩展点与测试入口

可扩展暂停、恢复、取消和多轮澄清命令；测试入口为幂等 ControlTurn、事务回滚和 ArchUnit。
 
