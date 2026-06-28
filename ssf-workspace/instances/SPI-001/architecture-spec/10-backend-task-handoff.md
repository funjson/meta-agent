# 10 Backend Task Handoff

## 必须遵守

1. 核心对象名称和层级不得另造替代模型。
2. 每个顶层模块采用 api/application/domain/infrastructure 分层。
3. Loop 不得引用 Job、Task 或 Control 包。
4. Intent 不得操作 Job。
5. 下层通过 Runtime 中立合同请求上层动作。
6. 所有类和方法有 Javadoc；状态迁移、授权合并、幂等、事务、恢复和外部调用边界有行级注释。
7. Prompt 使用稳定 ID、版本、变量 Schema 和内容哈希统一管理。
8. ORM 使用 MyBatis/MyBatis-Plus；AI 层暂不引入框架。
9. Control 是控制平面；必须通过 ControlTurn/ControlDecision 表达具体状态，禁止使用含糊的“Control 对象”。
10. HTTP Controller 只放在 Web Adapter，Conversation/Control 模块不得拥有对方的 Controller。
11. 并行 Task 通过持久化 Dispatch 与独立 TaskRun 租约执行；图推进必须锁 Job 并保持幂等。
12. Skill 脚本只能注册为 Tool executable，禁止 SkillCompiler 或 Loop 直接启动本地脚本。

## 完成定义

- 编译、Checkstyle、ArchUnit 和测试通过。
- 数据迁移可在空库执行。
- 旧双流程 Java 类型、表、枚举分支、Prompt 和路径节点不存在。
- README 与实现一致。
- DeepSeek Key 不出现在源码和测试输出。
