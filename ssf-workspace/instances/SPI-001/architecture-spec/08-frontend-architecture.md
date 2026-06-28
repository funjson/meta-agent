# 08 Frontend Architecture

## 页面

| 页面 | 目标 |
|---|---|
| Chat Workspace | 类 Codex 的聊天、任务创建和阶段反馈 |
| Conversation History | 历史会话列表、切换当前 Conversation |
| Job Detail | TaskGraph、根/父/子 Job、预算和状态 |
| Agent Path | 决策与执行路径、Tool、Skill、Evidence、Checkpoint、恢复 |
| Template Management | TaskGraphTemplate 版本、图校验和激活 |
| Authorization Inbox | 待授权差异、批准和拒绝 |
| Provider Settings | Secret 配置与连接测试 |

## Agent Path

```text
Conversation
  → ControlDecision
  → Root Job
      → TaskGraph / Task / TaskRun
          → LoopRun / LoopNode
              → Tool / Skill / Model / Evidence / Checkpoint
              → Child Job
                  → TaskGraph / Task / TaskRun / Loop...
```

Child Job 嵌套在发起它的 origin LoopNode 下。界面只显示结构化摘要和可审计事实，不显示隐藏思维链、完整 Prompt、Secret 或未脱敏 Tool 参数。

Agent Path 支持滚动、同层级点击展开/收起、复制为面向 Codex 调试的 Markdown/树形文本。Job、TaskRun、LoopNode 展示对象目标摘要，不把大段最终回复当路径摘要。

## 聊天消息可见性

- 聊天区只展示用户消息、用户可见结果、澄清问题、消歧问题和失败提示。
- `JOB_ACCEPTED`、恢复提交、Worker 调度等内部状态只进入 Agent Path / RuntimeEvent，不进入 Conversation 可见消息。
- 复制聊天记录应包含 message id、role、messageType、jobId、taskRunId 和正文，便于复盘。

## 长任务交互

- 聊天请求快速返回已创建对象，但不向消息流写入内部“任务已提交”文案。
- SSE 按 sequence 推送阶段完成、当前状态与下一阶段。
- 重连使用 Last-Event-ID 补齐事件。
- 主列表默认只显示根 Job，子 Job 在详情与路径中展开。
