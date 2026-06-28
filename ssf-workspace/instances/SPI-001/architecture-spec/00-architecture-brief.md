# 00 Architecture Brief

## 目标

Meta Agent Framework 是可配置的通用 Agent 平台。用户通过 `AgentProfile` 组合模型、Tool、Skill、Policy、Evaluator 与运行参数，创建数字人口播、健康助手等垂直 Agent。

平台的核心价值不是某个垂直场景，而是稳定的长任务执行、暂停与恢复、可观测、分层验收和评测。

## 核心对象模型

```text
AgentProfile
  → Job
      → TaskGraph
          → Task
              → TaskRun
                  → LoopRun
                      → LoopTree
                          → LoopNode
                              ├─ Child LoopNode
                              └─ may request blocking Child Job
```

配置型流程使用版本化 `TaskGraphTemplate`。模板在 Job 创建时实例化为普通 TaskGraph，因此运行期只有一套 Job/Task/Loop 语义。

## 核心控制关系

```text
Conversation Message
  → ControlTurn
      → ControlKernel
          ├─ Intent
          └─ Job → Task → Loop
```

- Conversation 只管理会话与消息。
- Intent 只识别任务语义、标签、风险和澄清需求，不创建 Job。
- Control 是控制平面，不是名为 `Control` 的领域实体。
- `ControlTurn` 是一条用户消息触发的可审计控制聚合；`ControlDecision` 是该轮的结构化决策结果。
- `ControlKernel` 管理聊天轮次、控制命令、Job 初始化和用户状态反馈。
- Job 管理 TaskGraph、模板匹配、子 Job、Job 调度和 Job 验收。
- Task 管理 TaskRun、Task Contract、执行策略和 Task 验收。
- Loop 负责最小 ReAct 闭环、局部规划、动作执行、Evidence 与 Loop 验收。
- Runtime 提供跨层中立合同、预算、AuthorityEnvelope 和派生请求。
- ContextEnvelope 是上下文事实源；Intent/Loop 只消费按目的装配出的 Prompt View。
- 用户消息不会默认绑定最近等待项；多 WAITING_HUMAN 场景必须先匹配或消歧。

HTTP Controller 属于 Web Adapter，不属于 Conversation 或 Control 领域模块。`POST /conversations/{id}/messages`
表达 Conversation 资源语义，但调用 `ControlKernel` 完成一轮控制处理。

## 并行 Task 与 Subagent

- TaskGraph 可以同时产生多个 READY Task；TaskCoordinator 将每个 Task 物化为独立 TaskRun 与持久化 Dispatch。
- Worker 通过租约和 `SKIP LOCKED`/条件更新并行领取，Job 完成事务按 Job 锁串行收敛依赖解锁与终态。
- Subagent 使用 `SubagentProfile` 表达角色、模型、Tool/Skill allowlist 和 AuthorityEnvelope。
- v0.1 的 Child Job 默认继承当前 AgentProfile；显式 SubagentProfile 是受控覆盖，不得扩大父级权限。

## Skill Package

Skill 不再仅被视为一段 `SKILL.md` 文本，而是版本化 `SkillPackage`：

```text
SkillPackage
  ├─ SKILL.md
  ├─ scripts/
  ├─ references/
  └─ assets/
```

脚本只能注册为受 AuthorityEnvelope 约束的 Tool executable，由 Tool Runtime 执行和审计；
SkillCompiler 不直接启动脚本。

## Child Job

LoopNode 只能返回 `ChildJobRequest`。JobCoordinator 在事务中创建子 Job、TaskGraph、父子关系和恢复事件。父 LoopNode 与 TaskRun 进入 `WAITING_CHILD_JOB`；子 Job 完成后，父 TaskRun 重新获取租约并把 `ChildJobOutcome` 作为动作结果继续执行。

v0.1 仅支持阻塞型子 Job，默认限制为最大深度 3、单 Job 最多 8 个直接子 Job、整棵树最多 32 个 Job。

## 不变量

1. Loop 不依赖 Job、Task 或 Control。
2. 下层不能直接完成上层对象。
3. 安全、权限和数据边界只能收窄。
4. Skill 的规范作用域默认只影响加载它的 Loop 子树。
5. 结构变化由请求表达，由拥有该结构的上层协调器物化。
6. 路径展示结构化决策摘要、动作、Tool、Skill、Evidence、Checkpoint 与恢复事实，不展示隐藏思维链。
