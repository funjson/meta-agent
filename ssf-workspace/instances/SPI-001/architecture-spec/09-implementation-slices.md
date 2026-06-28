# 09 Implementation Slices

## R1 架构基线

- 更新 SPI-001、核心对象模型和追踪矩阵。
- 建立 conversation/intent/control/job/task/loop/runtime 顶层模块。
- 增加 ArchUnit 依赖白名单。

## R2 TaskGraphTemplate

- 删除旧双流程代码、表、Prompt、路径节点与测试。
- 新增模板版本、匹配、实例化与动态规划兜底。
- 本地数据库通过新 Flyway 迁移后重建。

## R3 协调器与验收

- 拆分 JobCoordinator、TaskCoordinator、LoopExecutionCoordinator。
- 建立 Loop/Task/Job 三级 CompletionPolicy。
- 移除非空文本即正式完成策略，仅保留明确命名的基础测试实现。

## R4 策略与 Skill

- 实现 PolicyResolver、AuthorityEnvelope 与预算分配。
- 实现 SkillCompiler、不可变 Manifest、checksum 与非法权限拒绝。
- 实现 WAITING_APPROVAL 与 WAITING_HUMAN。

## R5 Child Job 与恢复

- 持久化 Worker、阻塞型 Child Job、等待、完成回传。
- 幂等、递归限制、暂停/取消传播、事件重放和崩溃点测试。

## R6 可观测与交付

- Agent Path 嵌套 Child Job。
- 顶层模块 README、Javadoc、关键行级注释。
- Checkstyle、ArchUnit、后端测试、前端测试和真实 DeepSeek 冒烟。

## R7 Control 与扩展能力收束

- 引入 ControlTurn/ControlDecision 所有权和 ControlKernel，Conversation 只保留会话与消息。
- Controller 下沉 Web Adapter；Agent Path 下沉 Observability 读模型。
- TaskRunDispatch 支持同一 Job 的并行 READY Task，Job 锁保证图推进收敛。
- 补齐 CHILD_JOB_COMPLETED、Outcome 持久化、父 TaskRun 租约恢复和事件重放。
- SkillPackage 支持 scripts/references/assets 清单；新增 SubagentProfile 受控派生合同。
