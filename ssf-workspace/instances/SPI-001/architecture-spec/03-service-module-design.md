# 03 Service Module Design

## 顶层模块

| 模块 | 职责 | 非职责 |
|---|---|---|
| conversation | Conversation、Message、可见消息查询和消息持久化 | 意图识别、Job 创建、执行、内部状态消息 |
| intent | Turn Understanding、混合意图、任务级意图改写、规则、模型、降级和结果校验 | 操作 Job、Task 或会话状态；生成 web/search 查询词 |
| clarification | ClarificationRequest、去重、轮次控制、回答恢复目标 | 意图识别、Job/Task 调度 |
| control | ControlTurn、ControlDecision、ControlKernel、控制命令和用户状态反馈 | Message 所有权、TaskGraph 内部调度、Loop 执行 |
| context | ContextEnvelope、ContextAssembler、LoopContextBuilder、结构化上下文块、Token 预算入口 | 模型调用、工具执行、消息持久化 |
| file | ConversationFile、文件上传、受控读写和文件上下文摘要 | 访问宿主机任意路径、执行脚本 |
| job | Job、TaskGraph、TaskGraphTemplate、子 Job、Job 调度与验收 | Loop 动作执行 |
| task | Task、TaskRun、TaskRunDispatch、Task Contract、并行派发与 Task 验收 | Job 模板匹配 |
| loop | LoopRun、LoopTree、LoopNode、局部规划、动作与 Loop 验收 | 依赖或修改 Job/Task/Control |
| runtime | AuthorityEnvelope、预算、合同、派生请求、中立事件合同 | 业务协调器 |
| capability | SkillPackage、SkillCompiler、资源校验和运行时 Capability Load | 绕过 Tool Runtime 直接执行脚本 |
| tool | ToolDefinition、ToolInvocation、ToolResult、Skill as Tool 目录 | 绕过权限执行外部副作用 |
| websearch | WebSearchClient、搜索源适配、网页读取、证据抽取和证据池持久化 | 最终回答生成、ToolInvocation 审计 |
| profile | AgentProfile 与 SubagentProfile | Job/Task/Loop 状态 |
| observability | Agent Path、事件投影与评测事实查询 | 修改执行状态 |
| web | HTTP Controller、请求校验和响应组装 | 领域状态所有权 |

## 依赖白名单

```text
Control → Job → Task → Loop
Control → Intent
Control → Conversation
Control/Job/Task/Loop → Clarification
Conversation / Intent / Control / Job / Task / Loop → Runtime
Intent / Loop → Context
Loop → Provider / Tool / Capability / Context
Context / Tool → File
Tool → WebSearch
Web → Conversation / Control / Job / Task / Observability
Observability → 持久化读模型
```

禁止 `Loop → Task/Job/Control`、`Task → Job/Control`、`Job → Control`。

## 协调器

- `JobCoordinator`：创建根/子 Job、实例化模板、推进 TaskGraph、传播暂停取消、执行 Job 验收。
- `TaskCoordinator`：创建与派发 TaskRun、管理租约入口、执行 Task 验收。
- `LoopExecutionCoordinator`：执行 LoopNode 阶段、Checkpoint、动作、ChildJobRequest 与 Loop 验收。
- `ControlKernel`：以 ControlTurn 为边界协调 Message、ContextEnvelope、PendingInteraction、Intent、Job 初始化、后台提交和用户状态反馈。
- `TurnUnderstandingService`：整轮用户输入理解入口，统一处理 pending 候选、新任务、控制命令、混合意图和任务级 canonicalGoal 改写；模型不可用时退回单动作安全降级。
- `ControlJobInitializationService`：从 Control 决策创建根 Job，收束 Provider 解析、TaskGraphTemplate 匹配、动态 TaskGraph 规划和 TaskGraph 级 Clarification 注册。
- `ControlUserResponseRenderer` / `ClarificationUserResponseRenderer`：用户可见消息渲染边界；ControlDecision、Clarification contract、Checkpoint 等系统对象不得直接进入聊天室。
- `TaskDispatchCoordinator`：原子创建 TaskRun/Dispatch，并允许多个 Worker 并行领取同一 Job 的独立 READY Task。
- `ChildJobCompletionCoordinator`：写入 ChildJobOutcome 和完成事件，再触发父 TaskRun 恢复。
- `RecoveryWorker`：周期扫描租约过期、等待子 Job 回传和可自动恢复 Checkpoint 的 TaskRun。
- `JobReplayService`：扫描已创建但未启动的 Job，补齐 Control 事务提交后 Worker 入队前崩溃的恢复缺口。
- `ContextAssembler`：构造统一上下文事实源，并生成 Intent / Loop Prompt View；当前不做最近 N 轮截断，后续压缩机制在这里接入。
- `LoopContextBuilder`：把目标、Conversation 投影、Observation、Skill、Tool 和约束构造成结构化上下文。
- `ReActActionPlanner`：Provider 不支持原生 tool/function calling 时的 fallback JSON planner；Skill Manifest 派生仍由它优先处理。
- `LoopCorrectionPolicy`：Loop Kernel 的确定性纠偏入口，负责阻断重复工具调用、漂移和失控趋势。

三者分别拥有自己的事务写入口，不共享“万能执行服务”。

## PolicyResolver

有效策略按 `Profile → Job → Task → Loop → Child Job` 计算：

- 安全、权限、数据边界取交集。
- 预算从父级剩余预算分配。
- Evidence、输出和验收要求合并并加强。
- Provider/Tool 仅能在上游 allowlist 内覆盖。
- 不可委托冲突直接拒绝。
- 可委托权限或预算不足进入 `WAITING_APPROVAL`。
- 输入或验收合同冲突进入 `WAITING_HUMAN`。

## SkillCompiler

Skill 导入时编译为不可变 Manifest，内容包括 `POLICY / STEP / CHILD_JOB`、可选模板引用或动态规划说明、ContractContribution 与 CapabilityRequest。编译产物通过 Schema、图和权限校验后自动激活，并保存原文、checksum、Prompt 版本与内容哈希。

SkillPackage 额外保存资源清单。`scripts/` 资源必须声明解释器、入口参数 Schema、副作用分类和 Tool ID；
运行时只允许 Tool Runtime 根据 AuthorityEnvelope 调用。`references/` 与 `assets/` 作为按需上下文资源加载。

## Tool 与 Skill

Skill 本身通过 Tool 体系被模型发现和加载：

```text
LoopContextBuilder 注入 ToolCatalog
  → Provider native tool_call 或 fallback ReActActionPlanner 选择 skill.search / skill.load
  → Skill 加载为当前 LoopNode 的局部 Capability
  → CapabilityScopeResolver 合并规范
  → CapabilityDerivationResolver 解析 STEP / CHILD_JOB
```

v0.1 已提供受控脚本执行器：解释器白名单、临时目录、清空环境变量、超时、输出截断、
`NONE/READ_ONLY` 副作用限制和 ToolInvocation 审计已经接入。后续仍需要更完整的沙箱、
授权审批、资源限额和 Worker replay 覆盖后再开放写入型副作用。

## Kernel 命名

`ControlKernel` 与 `LoopKernel` 是应用服务角色，不作为包根统一吞并领域模块。Job、Task、Loop、Runtime
继续平铺，以保持对象所有权和依赖方向清晰。

## 实现校准 r7：Tool / Skill 执行与 Clarification 恢复

- `tool` 已从“合同与目录”推进到可执行层：`ToolExecutionService` 维护 `tool_invocation`，支持 framework tool、受控脚本 Tool、`skill.load` 和 `clarification.request`。
- `loop` 已接入 Tool Runtime：`RuntimeExecutionService` 不再对 `TOOL_CALL / SKILL_LOAD` 抛未连接错误；普通 Tool 结果进入 Observation/Evaluation。
- `clarification.request` 是特殊 Tool：它创建 `ClarificationRequest`，随后由 `RuntimeTransactionService` 将 `LoopNode` 与 `TaskRun` 一起置为 `WAITING_HUMAN`。
- 用户回答后，`ControlTurnInitializer` 把回答绑定到原 `ClarificationRequest`；若来源是 Loop/ToolCall，则写入 `CLARIFICATION_ANSWERED` checkpoint，并由 `ControlJobWorker` 提交 `TaskRunResumeExecutor` 续跑。
- `observability` 已增加 `TOOL_CALL` Agent Path 节点，挂载在 origin `LoopNode` 下。

## 实现校准 r8：可见消息、Context 与 Pending Interaction

- `ConversationService.get` 只返回可见消息；`JOB_ACCEPTED`、恢复提交等内部调度状态不进入聊天上下文。
- `ContextEnvelope` 是 Conversation 级上下文事实源；LoopContext 是一次模型调用的 Prompt View，不是孤立的第二套上下文。
- `PendingInteractionMatcher` 处理多 waiting job / 多 Clarification 场景：高置信才绑定，否则创建新意图或返回消歧问题。
- `ClarificationNeedDetector` 将模型自然语言中的缺信息请求升级为正式 `clarification.request`，避免 Loop 因非空文本误完成。

## 实现校准 r9：Clarification Answer、PendingInteractionRouter 与可见消息

- TaskGraph 阶段澄清恢复必须先恢复 `Task WAITING_HUMAN → READY`，再恢复
  `Job WAITING_HUMAN → CREATED`；禁止依赖 MySQL 多表 UPDATE 的返回行数判断成功。
- `CONTROL_COMMAND_ACK` 只用于暂停、恢复、取消和状态查询等正式控制命令，不用于澄清回答。
- `CLARIFICATION_ANSWER` 是正式路由结果：命中 open clarification 后先合并历史部分事实并进行
  结构化完整性评估；完整时恢复 TaskGraph/Loop，未完整时只记录部分事实并保持等待。
  多个候选时进入消歧，不得落入控制命令 ACK。
- `PendingInteractionRouter` 已由模型结构化输出驱动，负责选择等待项、消歧和抽取补充事实；
  模型不可用、输出无效或 targetId 不在当前候选快照中时，降级到保守 Matcher。
  结构化事实会写入 Clarification/Task Contract，用于同类等待任务复用上下文，避免重复追问。
- `PendingInteractionCompletionPolicy` 根据原问题合同、模型 missingFields 和累计事实决定是否恢复；
  不完整回答不得启动 Loop，避免模型自由追问导致澄清问题漂移。
- 用户可见消息不需要比 Loop 输出更复杂：Control 层只负责必要的澄清、消歧和控制回执；
  这些消息可以直接展示给用户，但不能直接暴露审计型 `decisionSummary`。

## 实现校准 r10：结构化澄清合同与混合意图边界

- `IntentRecognition` 可输出 `clarificationQuestion` 与 `clarificationContract`。前者是用户可见
  自然语言问题，后者是系统用 JSON 合同；如果模型未给出合法合同，Control 使用通用合同兜底。
- `ClarificationRequest.contract_json` 持久化合同，后续多轮回答必须按同一合同判断，避免第二轮
  问题漂移。
- Loop 运行时自然语言追问升级为 `clarification.request` 时，也必须生成并持久化运行时合同；
  `ToolExecutionService` 支持 `contractJson` / `contract` 两种参数形态。
- `PendingInteractionCompletionPolicy` 只允许默认授权补齐 `requiredLevel=SOFT/OPTIONAL`
  且 `defaultable=true` 的槽位；`BLOCKING` 关键槽位仍缺失时，必须继续等待用户补充。
- `ControlJobInitializationService` 对低风险生成任务做澄清合同归一化：用途、背景、风格、
  长度等质量字段可从模型误标的硬阻塞降为 SOFT；天气地点、文件、检索、工具参数、权限、
  成本和外部副作用不做放松。
- `EXPLAIN_PENDING_REQUIREMENTS` 只解释当前合同还需要的信息，不绑定回答、不恢复任务、不创建 Job。
- 没有 open pending interaction 时，`CLARIFICATION_ANSWER` 只能进入无目标保护提示，
  不能被局部规则改写为新任务；混合意图必须通过 `TurnRoutingPlan` 明确表达。

## 实现校准 r11：Control 职责拆分与 Agent Path 降噪

- `ControlTurnInitializer` 不再直接负责 Provider 解析、模板匹配、TaskGraph 动态规划和 TaskGraph
  Clarification 注册；这些职责收束到 `ControlJobInitializationService`。
- Control-only 用户响应由 `ControlUserResponseRenderer` 输出，澄清帮助和部分回答追问由
  `ClarificationUserResponseRenderer` 输出，避免审计摘要、字段 key 或 JSON 泄漏到聊天室。
- Agent Path 后端 label 使用阶段语义，不直接暴露 `LoopNode #n`；前端默认简洁模式隐藏
  Phase、Checkpoint、ModelCall、RecoveryAttempt，调试模式和复制导出保留完整路径。

## 实现校准 r12：混合意图、Conversation Fact 与 ControlActionExecutor

- `TurnRouter` 是 Control 轮次路由门面，正式能力由 `TurnUnderstandingService` 提供：
  模型一次性看到用户消息、Conversation Context、open pending candidates、已解决事实和当前时间，
  输出 `TurnRoutingPlan(TurnIntentGraph)`。
- 混合意图以模型输出的 `TurnIntentGraph.nodes[] / edges[]` 为主。`nodes[]` 表示一句话里的
  pending 回答、新 Job、控制命令或消歧节点；`edges[]` 表示独立、结果依赖、顺序依赖或消歧关系。
  降级路径只产出保守单节点图，不再通过 `TurnPlanRepairer` / `MixedTurnSegmenter` 做规则拆分。
- `ControlTurnGraphCompiler` 将 `TurnIntentGraph` 编译为 `ControlExecutionPlan`。某节点进入澄清或等待时，
  只阻塞依赖它的下游节点，不阻塞无关兄弟节点。
- `CREATE_JOB` 节点通过 `JobInitializationSpec` 创建根 Job，使 nodeId、taskType、canonicalGoal、
  labels、risk 与 clarification contract 都保持节点级隔离，避免混合任务之间互相污染。
- `CREATE_JOB` action 可以携带 `canonicalGoal` 与 `IntentRewrite`。这是任务级改写，只把口语化
  表达规范成清晰 Job 目标；web/search 查询词改写继续放在 Research/Loop/Tool Planning。
- `TurnRoutingPlan` 由 `TurnAction` 组成，当前覆盖 `ANSWER_PENDING`、`CREATE_JOB`、
  `ASK_DISAMBIGUATION`、`EXPLAIN_PENDING_REQUIREMENTS`、`CONTROL_MESSAGE`
  和 `CLARIFICATION_NO_TARGET`。
- `ControlActionExecutor` 负责消费 Plan：创建 Job、恢复 Clarification、写入结构化
  `conversation_fact`、生成后台 `ControlDispatchCommand`。当澄清回答仍不完整但同轮还有独立
  `CREATE_JOB` 时，不再无条件停止后续 action；即时澄清消息仍绑定等待中的 Job。
  Intent 层仍然不操作 Job。
- `conversation_fact` 保存 Conversation 级结构化事实，来自已解决澄清或部分澄清回答；
  ContextAssembler 将这些事实注入 Intent 与 Loop Prompt View，但不作为聊天消息展示。

## 实现校准 r13：持久化 Worker replay 与 ReAct 工具选择

- `JobReplayService` 扫描 `CREATED + READY task + no task_run` 的 Job，由 `ControlJobWorker`
  周期性重投递，覆盖 Control 提交成功但本机 Worker 未启动的崩溃点。
- `ReActActionPlanner` 接入 `loop.action-planning.v1` Prompt，作为不支持原生工具调用 Provider 的
  fallback JSON planner。Skill Manifest 只负责 STEP / CHILD_JOB 派生；通用工具选择优先由
  Provider native tool/function calling 完成。
  非法 JSON 不再静默降级为旧的硬编码 `MODEL_CALL`。
- `LoopActionResult.fromTool` 保留规划阶段动作类型，Agent Path 和 Evaluation 可以区分
  `TOOL_CALL / RAG_QUERY / FILE_SEARCH / SKILL_LOAD / CLARIFICATION_REQUEST`。
- `LoopEvaluator` 将 Tool/RAG/File/Skill 结果视为 Observation，不直接作为最终用户回复；
  下一轮 Child LoopNode 负责基于 Observation 继续生成用户可见结果。

## 实现校准 r14：网络搜索 Tool

- 新增 `websearch` 模块，默认实现为 `BingRssWebSearchClient`，通过 RSS 返回 title/url/snippet。
- `ToolCatalogService` 暴露 `web.search`，`ReActActionPlanner` 可选择 `WEB_SEARCH` 或
  `TOOL_CALL + web.search`。
- `ToolExecutionService` 执行 `web.search` 后把结果写入 `tool_invocation.result_json`；
  `LoopEvaluator` 将 `WEB_SEARCH` 视为中间 Observation，下一轮模型基于来源摘要合成答案。
- 外部网页内容属于不可信输入；搜索摘要只作为候选线索。读取网页正文、证据抽取、
  来源持久化和引用复盘由 `web.fetch` / `web.extract` 与 Web Research 证据池承担。
  Deep Research 的来源去重、证据矩阵和报告生成由 Job/TaskGraph 编排。

## 实现校准 r15：Native Tool Calling、纠偏与 Thinking Mode

- `ModelRequest` 增加 `tools` 与 `thinkingMode`，`ModelResponse` 增加 `toolCalls` 与
  `reasoningContent`。Loop 不再把“动作选择”固定成一次额外 planner 模型调用。
- 支持原生工具调用的 Provider 路径：

```text
LoopPlan(MODEL_CALL)
  → ModelProvider.generate(prompt + ModelToolSpec)
  → content：进入 Observation/Evaluation
  → toolCalls：Runtime 映射回内部 Tool ID 并执行 ToolExecutionService
```

- `ToolCatalogService.modelToolSpecs()` 将内部 `web.search` 等 Tool ID 转成 Provider 安全函数名，
  例如 `web_search`；响应解析后再映射回内部 Tool ID。
- `LoopCorrectionPolicy` v0.1 先实现确定性纠偏：已有工具 Observation 的下一轮模型调用不再暴露
  工具 Schema，防止 `web.search → Observation → web.search` 这类循环。
- Thinking/Reasoning 进入 Provider 合同，但 v0.1 默认关闭。DeepSeek reasoner 不暴露工具调用，
  只解析 `reasoning_content`；GLM 等后续 Provider 可将 `ModelThinkingMode` 映射到官方 thinking 配置。

## 实现校准 r16：Web Research 证据池与 Agent Path

- `websearch` 新增 `WebResearchStore`，把 `web.fetch` 产生的 `WebSourceDocument` 和
  `web.extract` 产生的 `WebEvidenceItem` 持久化为正式证据池。
- `ToolInvocation` 继续只审计“调用了哪个工具、参数和结果是什么”；来源和证据归
  `websearch` 所有，并通过 `tool_invocation_id` 挂回 Agent Path。
- Agent Path 新增 `WEB_SOURCE` 与 `WEB_EVIDENCE` 节点，结构为：

```text
LoopNode
  → ToolCall(web.fetch / web.extract)
      → WEB_SOURCE
          → WEB_EVIDENCE
```

- `web.search` 仍只代表候选检索，不把未读取网页的搜索摘要写成证据；后续 deep-research
  的多轮搜索、来源去重、证据矩阵和报告生成应由 Job/TaskGraph 编排，不塞进单个 Tool。

## 实现校准 r17：生产级 Web Search / Deep Research

- `web.search` 从临时工具结果升级为可观测检索链路：新增 `web_search_run` 与
  `web_search_candidate`，记录查询、候选、排名、来源类型和 Provider。
- Agent Path 结构扩展为：

```text
LoopNode
  → ToolCall(web.search)
      → WEB_SEARCH_RUN
          → WEB_SEARCH_CANDIDATE
  → ToolCall(web.fetch / web.extract)
      → WEB_SOURCE
          → WEB_EVIDENCE
```

- `LoopContextBuilder` 将同一 Job 的 Web Research Evidence Pool 注入后续 Loop Context，
  并明确区分 `SEARCH/CANDIDATE`（线索）与 `SOURCE/EVIDENCE`（可作为依据的已读取内容）。
- `LoopCorrectionPolicy` 从“一旦有工具 Observation 就关闭全部工具”调整为按 Tool ID
  过滤：`web.search` 后允许 `web.fetch/web.extract`，`web.fetch` 后允许 `web.extract`，
  `web.extract` 后收敛到 `MODEL_CALL`。
- Job 层新增 `DefaultResearchTaskGraphFactory`，当无配置型 `TaskGraphTemplate` 且意图标签
  显式为 `research-depth:deep-research` 时，创建默认研究 TaskGraph：

```text
research-plan
  → source-discovery
      → source-reading
          → evidence-matrix
              → report-synthesis
                  → quality-review
```

- 默认 Deep Research 仍然完全落在 `Job → TaskGraph → Task → TaskRun → LoopRun`，
  不恢复 Workflow 概念；配置型 `TaskGraphTemplate` 后续可以覆盖默认图。

## 实现校准 r18：混合意图 Job 作用域隔离

- `TurnIntentNode` 只描述一轮输入里的任务节点；每个可执行节点创建独立 root `Job`。
  Job 创建时把节点语义固化为 `TaskIntentScope`，并写入 `job.effective_policy_snapshot.intentScope`。
- `TaskIntentScope` 包含 `turnNodeId`、`taskType`、`sourceSpan`、`canonicalGoal`、
  澄清合同快照与 `allowedToolIds`。Loop 运行时只读取该快照，不反向依赖 Intent/Control。
- `conversation_fact` 分为稳定跨任务事实与 Job 级任务参数：
  - `CONVERSATION`：姓名、昵称等稳定身份事实，可以被同一会话内多个 Job 复用。
  - `JOB:<jobId>`：用途、风格、长度、地点等任务参数，只注入所属 Job。
- `TaskScopedContextProjector` 在 Loop Context 构建阶段保留可见聊天历史，但按
  `TaskIntentScope + jobId` 过滤结构化事实、等待澄清和已解决澄清事实，避免兄弟任务互相污染。
- `LoopToolExposurePolicy` 以 `TaskIntentScope.allowedToolIds` 为主边界。历史 Job 没有
  `TaskIntentScope` 时才回退到旧的目标文本启发式，防止“同一句话里有天气任务”导致个人介绍
  Job 暴露 `weather.current`。
- 澄清恢复写入事实时使用当前 Job 作用域；稳定身份事实自动提升到 Conversation 作用域。
  因此“我叫冯建松，另外查天气”会创建两个 Job：个人介绍 Job 可复用姓名，天气 Job 可使用
  地点/天气工具，但两者的任务参数、澄清合同和工具集合互不覆盖。
