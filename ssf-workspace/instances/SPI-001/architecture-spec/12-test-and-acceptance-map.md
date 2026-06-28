# 12 Test and Acceptance Map

## 实现校准 r7

- AC-007/AC-008 扩展：除 Child Job 外，`CLARIFICATION_ANSWERED` 也必须能通过 `TaskRunResumeExecutor` 恢复原 LoopNode。
- AC-013 扩展：`tool`、`clarification`、`loop` README 必须描述 ToolInvocation、WAITING_HUMAN 和用户回答恢复链路。
- AC-014 扩展：后端验证需覆盖新增 Mapper、Checkstyle、ArchUnit 和测试。
- AC-015 扩展：真实模型冒烟不得泄露环境变量中的 API Key。

| ID | 验收项 |
|---|---|
| AC-001 | 模块依赖符合白名单且不存在反向引用 |
| AC-002 | Intent 只输出语义与标签，不创建 Job |
| AC-003 | 模板匹配、动态规划兜底和 DAG 校验正确 |
| AC-004 | Skill 编译确定、checksum 稳定、非法权限被拒绝 |
| AC-005 | 策略只收窄、预算正确分配、验收要求合并加强 |
| AC-006 | Child Job 幂等创建，深度/数量/整树限制生效 |
| AC-007 | Child Job 完成回传并从 origin LoopNode 恢复 |
| AC-008 | 创建、派发、完成和父恢复崩溃点可安全续跑 |
| AC-009 | 暂停/取消传播到阻塞型子 Job |
| AC-010 | 子 Job 已完成但父未恢复时可事件重放 |
| AC-011 | Loop、Task、Job 只能完成自己所有的层级 |
| AC-012 | 旧双流程类型、表、Prompt 和路径节点不存在 |
| AC-013 | 顶层模块 README、Javadoc 和关键行级注释完整 |
| AC-014 | Checkstyle、ArchUnit、后端与前端测试通过 |
| AC-015 | 真实 DeepSeek 冒烟通过且 Secret 无泄漏 |
| AC-016 | Conversation 不依赖 ControlDecision，ControlTurn 幂等且 ControlKernel 边界明确 |
| AC-017 | 内部调度状态不进入聊天消息上下文，Conversation 只返回可见消息 |
| AC-018 | 多个 WAITING_HUMAN 候选时，用户消息需匹配/消歧，不默认绑定最近请求 |
| AC-019 | Loop 模型输出补充信息请求时必须升级为 ClarificationRequest，不得非空即完成 |
| AC-020 | 前端支持历史会话列表、复制聊天记录和复制 Agent Path |
| AC-021 | 澄清回答必须先通过结构化完整性评估；部分回答保持 OPEN，完整回答才恢复执行 |
| AC-017 | 多个独立 READY Task 可并行派发，完成时依赖解锁和 Job 终态只提交一次 |
| AC-018 | CHILD_JOB_COMPLETED 可重放并自动恢复 origin TaskRun |
| AC-019 | SkillPackage 路径安全、资源 checksum、脚本 executable 元数据和 SubagentProfile 权限收窄通过 |

## 故障注入点

- ChildJobRequest 已保存但子 Job 未创建。
- 子 Job 已创建但父等待状态未提交。
- 子 Job 完成但 Outcome 未写入。
- Outcome 已写入但父 TaskRun 未重新获取租约。
- 父恢复执行完成但完成事件未发布。

每个点必须证明重试不会重复创建结构、重复扣减预算或重复执行不可重放副作用。
