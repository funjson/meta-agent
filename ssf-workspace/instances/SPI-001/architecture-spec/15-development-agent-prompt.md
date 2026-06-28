# 15 Development Agent Prompt

实现 Meta Agent Framework SPI-001。

## 权威输入

- workspace：`C:\Users\funjson\Documents\meta-agent`
- SPI：`C:\Users\funjson\Documents\meta-agent\ssf-workspace\instances\SPI-001`
- architecture：`architecture-spec/`

## 实现目标

```text
AgentProfile
  → Job
      → TaskGraph
          → Task
              → TaskRun
                  → LoopRun
                      → LoopTree
                          → LoopNode
                              └─ may request blocking Child Job
```

严格采用 `ControlKernel → Job → Task → LoopKernel`，并把 Conversation、Intent 与 Runtime
中立合同独立出来。Control 是控制平面，具体状态使用 ControlTurn/ControlDecision。
Controller 属于 Web Adapter。配置型流程使用 TaskGraphTemplate。LoopNode 只能提出
ChildJobRequest，JobCoordinator 负责物化和恢复。

## 执行顺序

1. 同步架构与追踪。
2. 迁移顶层模块并建立 ArchUnit 白名单。
3. 删除旧双流程实现，引入 TaskGraphTemplate。
4. 拆分三级 Coordinator 与 CompletionPolicy。
5. 实现 PolicyResolver、SkillCompiler 和授权状态。
6. 实现持久化 Worker、阻塞型 Child Job 和恢复。
7. 补充 README、注释、测试与真实冒烟。
8. 使用持久化 TaskRunDispatch 并行领取独立 READY Task。
9. 补齐 ChildJobOutcome 回传与父 TaskRun 自动恢复。
10. SkillPackage 支持 scripts/references/assets；脚本只能经 Tool Runtime 执行。

每次核心对象、API、数据或恢复语义变化，都必须同步 SPI-001。
