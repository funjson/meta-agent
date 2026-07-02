# 01 Domain Model

## Turn-level Orchestration

用户一条消息可能同时包含多个语义节点：对 pending job 的补充、新任务、控制命令、消歧请求，或者多个互相依赖/互相独立的任务。系统在 Job 创建之前增加一层正式的 Turn 级编排对象：

```text
Conversation Message
  -> TurnUnderstanding
      -> TurnIntentGraph
          -> TurnIntentNode
          -> TurnIntentEdge
  -> ControlExecutionPlan
  -> Job / Clarification / Control feedback
```

`TurnIntentGraph` 不替代 `Job -> TaskGraph -> Task -> Loop`。它只回答“这一轮用户输入里有几个任务，以及它们之间是什么关系”。每个 `TurnIntentNode` 如果需要执行，会再创建自己的 root `Job`；每个 `Job` 内部仍然由 `TaskGraph` 表达 Task 依赖。

核心对象：

| 对象 | 所属模块 | 说明 |
|---|---|---|
| TurnIntentGraph | intent | 一轮用户输入的语义编排图，包含节点、依赖边和审计摘要。 |
| TurnIntentNode | intent | 一句话里的一个任务片段、pending 回答、控制命令或消歧节点。 |
| TurnIntentEdge | intent | 节点之间的关系，例如 `DEPENDS_ON_RESULT`、`MUST_RUN_AFTER`、`INDEPENDENT`。 |
| TurnTaskType | intent | 稳定任务类型枚举，例如 `WEATHER_QUERY`、`DEEP_RESEARCH`、`FILE_QA`、`RESUME_OR_PROFILE_GENERATION`。 |
| ControlExecutionPlan | control | Control 可执行编排计划，由 `ControlTurnGraphCompiler` 从 TurnIntentGraph 编译而来。 |
| JobInitializationSpec | control | 从单个 TurnIntentNode 编译出的 Job 初始化输入，隔离 nodeId、taskType、canonicalGoal、labels/risk/contract。 |

阻塞传播规则：

- 某个节点需要澄清或等待，只阻塞依赖它的下游节点。
- 无依赖的兄弟节点可以继续创建 Job 或恢复 pending。
- 消歧和无目标澄清属于保护性节点，会停止本轮剩余执行。

## 聚合与所有权

| 对象 | 类型 | 所有者 | 说明 |
|---|---|---|---|
| AgentProfile | Aggregate | profile | Agent 配置与上游策略 |
| Conversation | Aggregate | conversation | 会话与 Message |
| ControlTurn | Aggregate | control | 单条用户消息触发的控制轮次、幂等状态与关联 Job |
| ControlDecision | Entity | control | 意图结果、约束、TaskGraph 摘要和可审计路由决策 |
| IntentRecognition | Value | intent | 任务语义、标签、风险、置信度；不生成最终回复 |
| TurnUnderstanding | Value | intent | 一整轮用户输入的结构化动作计划，包含混合意图和任务级改写 |
| IntentRewrite | Value | intent | 口语化任务表达到 canonicalGoal 的审计记录，不包含查询词改写 |
| ClarificationRequest | Aggregate | clarification | 缺失信息、阻塞原因、问题、回答和恢复目标 |
| ContextEnvelope | Value | context | Conversation 级上下文事实源，供 Intent/Loop 生成 Prompt View |
| LoopContextSnapshot | Value | context | ReAct Loop 的结构化上下文快照 |
| ToolDefinition | Value | tool | 模型可选择的工具合同；Skill 也以 Tool 形式暴露 |
| ToolInvocation/ToolResult | Entity/Value | tool/loop | Tool 调用审计和 Observation 输入 |
| WebSearchRun | Entity | websearch | 一次 `web.search` 查询及结果数量，可挂回 ToolInvocation |
| WebSearchCandidate | Entity | websearch | 搜索返回的候选来源线索，不等同于已核验证据 |
| WebSourceDocument | Entity | websearch | 已读取并清洗的公开网页来源，可挂回 ToolInvocation |
| WebEvidenceItem | Entity | websearch | 从 WebSourceDocument 抽取的可引用证据片段 |
| Job | Aggregate | job | 整体目标、TaskGraph、父子 Job 和全局验收 |
| TaskGraphTemplate | Aggregate | job | 版本化配置型图模板 |
| TaskGraph | Entity | job | Job 内 Task 与依赖关系 |
| Task | Entity | task | 可验收工作单元 |
| TaskRun | Aggregate | task | Task 的一次执行尝试、租约和恢复入口 |
| LoopRun | Aggregate | loop | 一个可恢复执行闭环 |
| LoopTree | Structure | loop | LoopNode 父子结构 |
| LoopNode | Entity | loop | 最小可管理执行单元 |
| ChildJobRequest | Contract | runtime | Loop 向 Job 层提出的派生请求 |
| AuthorizationRequest | Aggregate | runtime/job | 需要用户决定的权限或预算差异 |
| TaskRunDispatch | Entity | task | TaskRun 的持久化派发、领取、重试和完成状态 |
| SkillPackage | Aggregate | capability | SKILL.md、脚本、引用和资产的不可变版本包 |
| SkillResource | Entity | capability | SkillPackage 中的脚本、引用或资产 |
| SubagentProfile | Aggregate | profile | Child Job 可选的角色、模型、Skill/Tool 与权限覆盖 |

## 状态

- Job：`CREATED → RUNNING ↔ WAITING_APPROVAL/WAITING_HUMAN/PAUSED → COMPLETED/FAILED/CANCELLED`
- Task：`BLOCKED/READY → RUNNING ↔ WAITING_CHILD_JOB/WAITING_APPROVAL/WAITING_HUMAN/PAUSED → COMPLETED/FAILED/CANCELLED`
- TaskRun：`CREATED/READY → RUNNING ↔ WAITING_CHILD_JOB/WAITING_APPROVAL/WAITING_HUMAN/PAUSED → COMPLETED/FAILED/CANCELLED`
- LoopRun：`CREATED → RUNNING ↔ WAITING_CHILD_JOB/WAITING_TOOL/WAITING_HUMAN/PAUSED → COMPLETED/FAILED/CANCELLED`
- LoopNode：与 LoopRun 等价，但额外允许 `WAITING_CHILDREN`。
- ControlTurn：`INITIALIZING → COMPLETED/FAILED`；同一幂等键只能产生一个 ControlTurn。
- TaskRunDispatch：`PENDING → CLAIMED → COMPLETED/FAILED`，过期 CLAIMED 可重新领取。
- ClarificationRequest：`OPEN → ANSWERED → RESOLVED/CANCELLED`；只有存在明确澄清请求时才进入人工等待语义。

## LoopNode 内部阶段

```text
CONTEXT_BUILD
  → PLANNING
  → ACTION_PREPARATION
  → ACTION_EXECUTION
  → OBSERVATION
  → EVALUATION
  → optional ADJUSTMENT
```

`CONTEXT_BUILD` 由 `LoopContextBuilder` 构造结构化上下文块，包含 system、goal、memory、
observation、capability、tool catalog 和 constraints。阶段是 LoopNode 内部审计记录，
不是新的核心对象。只有确实需要新闭环时才派生 Child LoopNode。

## Intent 与直接回答

Intent 只输出语义、标签、风险和任务类型提示，不输出最终回答。

简单问答和寒暄使用 `CHAT_QA`，仍由 Control 创建单节点 Job，再由 Loop Kernel 判断是否直接回答、
调用工具、检索、澄清或派生。这样可以保持所有用户交互都具备路径、恢复和评测事实。

用户消息不能默认绑定到最近的 Clarification。Control 在进入执行前先收集 Conversation 下所有
open pending interactions，并交给 Intent 模块的 Turn Understanding 统一判断：

- 命中某个 Clarification：先绑定回答并做结构化完整性评估；合同满足后才恢复原 Job/TaskRun，未满足时保持原请求 `OPEN`。
- 新意图：创建新 Job 或普通聊天，原等待项保持等待。
- 多候选不确定：向用户发起消歧，不恢复任何等待点。
- 混合意图：一轮消息可以同时产生 `ANSWER_PENDING`、`CREATE_JOB`、`CONTROL_MESSAGE`
  等多个有序 action。

`TurnUnderstandingService` 是正式入口。模型可用时，输入包含 Conversation Context、open waiting
interactions、上一轮消歧状态、已抽取事实和当前时间；输出必须是结构化 `TurnRoutingPlan`，
而不是直接用户回复。结构化事实至少覆盖姓名、身份/角色、用途、
目标对象、风格、长度、约束和期望产物。若两个等待项语义等价，且 Conversation 中已有事实
已经满足后一个等待项合同，系统应复用事实并恢复等待任务，不应重复向用户追问。
回答只能写入系统用结构化事实；用户可见消息继续使用原 Clarification 问题或确定性渲染文本，
禁止把结构化 JSON 暴露给用户。若当前没有任何 open pending interaction，即使模型误判为
`CLARIFICATION_ANSWER`，也不得擅自恢复。

`IntentRewrite` 只做任务级 canonicalGoal 改写：保留用户明确事实、补齐表达结构、解释相对时间，
但不得生成具体 web.search 查询词、site: 查询、工具参数或来源读取计划。查询级改写归
Research/Loop/Tool Planning 所有。

## 澄清语义

`WAITING_HUMAN` 不能由“没有 READY Task”反推。进入人工等待必须存在明确的
`ClarificationRequest` 或 HumanInterventionRequest。

澄清请求至少包含 reasonType、sourceType、question、contract_json、resume target 和 maxRounds。
其中 `question` 是面向用户的自然语言问题，可以由模型生成并经过系统兜底；`contract_json`
是系统用结构化合同，描述必填槽位、`BLOCKING/SOFT/OPTIONAL` 阻塞等级、可默认槽位、
别名、默认授权短语和输出约束。默认授权只能补齐合同中 `requiredLevel=SOFT/OPTIONAL`
且 `defaultable=true` 的槽位，不能绕过工具参数、权限、外部副作用、天气地点等
`BLOCKING` 关键输入。低风险生成任务允许把用途、背景、风格、长度等质量字段作为 SOFT
字段处理，用户明确“就这些/默认/随意”后可带默认假设恢复。
多轮部分回答可以把已抽取事实写入 resolution_json 作为部分决议快照，但状态仍保持 `OPEN`；
只有完整性策略判定合同满足时，才允许 `OPEN → ANSWERED → RESOLVED` 并恢复执行。

## 派生规则

`LoopNode` 可以产生：

- Child LoopNode：扩展当前 LoopTree。
- ChildJobRequest：请求上层创建阻塞型子 Job。
- AuthorizationRequest：请求用户批准可委托权限或额外预算。
- ToolInvocation：请求 Tool Runtime 执行工具。
- WebSearchRun / WebSearchCandidate：记录 `web.search` 发现了哪些候选来源。
- WebSourceDocument：记录 `web.fetch` / `web.extract` 实际读取过的来源。
- WebEvidenceItem：记录 `web.extract` 从来源中抽取出的证据片段。
- ClarificationRequest：请求用户补足当前动作所需输入。

Loop 只产生请求和结果，不持久化 Job/Task，不修改上层状态。

Loop Completion 不能使用“非空文本即完成”。模型若输出补充信息请求，必须升级为
`clarification.request`；模型若泄露 LoopNode / TaskRun / Observation 等内部术语，不得通过用户可见验收。

## 分层验收

- `LoopCompletionPolicy` 验证局部目标、动作结果、Evidence 与预算。
- `TaskCompletionPolicy` 验证 Task Contract、产物和重试条件。
- `JobCompletionPolicy` 验证 TaskGraph、整体目标和全局约束。

子 Job 必须先通过自己的 Job 验收；父 Loop 仍需消费结果并完成本层验收。

## 所有权不变量

1. Conversation 不保存或查询 ControlDecision。
2. ControlTurn 引用 Conversation/Message，但不取得 Message 所有权。
3. Web Adapter 可以组合 Conversation 与 Control 用例，领域模块之间不得通过 Controller 建立反向依赖。
4. Skill 脚本是资源和 Tool executable，不是模型可直接执行的任意文件。
5. SubagentProfile 的有效策略必须由父 Job 策略与子配置求交集。
6. Skill 作为 Tool 暴露，模型通过 `skill.search` 和 `skill.load` 命中并加载 Skill。
