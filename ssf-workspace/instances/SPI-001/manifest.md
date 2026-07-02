# SPI-001 Manifest

## 基本信息

| 字段 | 值 |
|---|---|
| spi_id | SPI-001 |
| project | Meta Agent Framework |
| ssf_workspace_path | `C:\Users\funjson\Documents\meta-agent\ssf-workspace` |
| target_instance | `C:\Users\funjson\Documents\meta-agent\ssf-workspace\instances\SPI-001` |
| architecture_output | `architecture-spec/` |
| input_mode | user_authorized_skip_product_design |
| frontend_input_mode | browser_ui_without_formal_prototype |
| review_status | ready_for_code_architecture_review |
| blocked | no |
| updated_at | 2026-06-28 |

## 已确认架构基线

- Java 21、Spring Boot 3.5.15、MyBatis-Plus 3.5.16、MySQL、浏览器 UI。
- v0.1 为单用户、单机、模块化单体；DeepSeek 是首个真实模型 Provider。
- 核心对象固定为 `AgentProfile → Job → TaskGraph → Task → TaskRun → LoopRun → LoopTree → LoopNode`。
- 配置型流程统一表达为版本化 `TaskGraphTemplate`，不保留第二套流程运行对象。
- `LoopNode` 可以提出阻塞型 `ChildJobRequest`，但只能由 `JobCoordinator` 物化子 Job。
- 依赖方向固定为 `Control → Job → Task → Loop`，Control 同时依赖 Intent 与 Conversation；各层只依赖 Runtime 中立合同。
- Control 是控制平面；具体状态由 `ControlTurn` 与 `ControlDecision` 表达，HTTP Controller 属于 Web Adapter。
- TaskGraph 允许多个独立 READY Task 经持久化 Dispatch 并行运行，Job 锁负责依赖和终态收敛。
- Skill 导入时编译为不可变 Manifest；运行时不重新解释核心规则。
- Skill 使用版本化 SkillPackage，允许 scripts/references/assets；脚本只注册为 Tool executable。
- Subagent 使用受父策略约束的 SubagentProfile，并通过 Child Job 派生。
- 安全、权限与数据边界只能收窄；预算从父级剩余值分配。
- 中断恢复、长任务、可观测、分层验收和 Agent 评测属于核心能力。
- RAG 与多 Agent 延后到后续 SPI。
- Secret 只来自环境变量或用户页面配置，不进入源码、日志、事件、路径或数据库明文。
- Intent 不生成最终回复；`CHAT_QA` 与普通任务都进入 Job/Loop。
- Clarification 是独立模块，`WAITING_HUMAN` 必须有结构化原因。
- Loop 使用 `LoopContextBuilder` 构建 ReAct 上下文，并通过 ToolCatalog 暴露 Skill as Tool。
- `ControlKernel.send()` 只做初始化与后台提交，Job 执行由本机 Worker 异步完成。

## 文档权威性

2026-06-20 的“核心架构收束”替代此前 SPI-001 中关于独立流程运行对象的设计。`architecture-spec/` 当前版本是唯一有效架构基线。

## 风险与 Gate

| Gate | 状态 | 说明 |
|---|---|---|
| product-design | risk_accepted | 用户授权跳过正式产品设计 |
| architecture | approved_for_implementation | 用户已确认本次收束方案 |
| code-architecture-acceptance | pending | 功能验收前先进行代码与架构验收 |
| functional-acceptance | pending | 代码架构验收通过后执行 |

## 运行记录

| 日期 | Run | 结果 |
|---|---|---|
| 2026-06-18 | architecture-run | 创建 SPI-001 架构基线 |
| 2026-06-19 | runtime-baseline | 完成聊天、TaskGraph、Loop、恢复与路径首批实现 |
| 2026-06-20 | architecture-convergence | 删除双流程模型，收束为 TaskGraphTemplate 与阻塞型 Child Job |
| 2026-06-20 | backend-convergence-r1-r4 | 顶层模块、V13、模板 API、Child Job 物化、三级验收、PolicyResolver、授权 API、SkillCompiler 和 53 项测试完成 |
| 2026-06-21 | architecture-repair-control-runtime | 明确 ControlTurn/ControlKernel、Web Adapter、并行 Dispatch、Child Job 回传与 SkillPackage/SubagentProfile |
| 2026-06-21 | implementation-verification-r5 | 后端 59 项测试、ArchUnit、Checkstyle、前端 build/lint、Flyway V14 与真实 DeepSeek 冒烟通过 |
| 2026-06-27 | clarification-context-tool-repair-r6 | 新增 Clarification/Context/Tool，Intent 去直接回复，Control 异步提交，RecoveryWorker，V15 与 59 项测试通过 |
| 2026-06-28 | clarification-contract-r10 | 澄清合同 contract_json、用户可见问题、默认授权收窄、EXPLAIN_PENDING_REQUIREMENTS 与混合意图边界 |
| 2026-06-28 | runtime-clarification-contract-r11 | Loop 自然语言澄清升级时生成运行时合同，clarification.request 持久化 contractJson，默认/没有了可收口可默认字段 |
| 2026-07-01 | mixed-intent-scope-r18 | 混合意图节点创建独立 Job，TaskIntentScope 固化到 Job 策略快照，Context/Tool/Clarification 按 Job 作用域隔离 |
