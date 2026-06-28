# 07 Event, Job and Integration

## 实现校准 r7

Clarification 恢复链路：

```text
LoopPlan(CLARIFICATION_REQUEST)
  → ToolExecutionService executes clarification.request
  → ClarificationRequest OPEN + ToolInvocation linked
  → LoopNode/TaskRun WAITING_HUMAN
  → user answer in next chat turn
  → ClarificationRequest RESOLVED
  → checkpoint CLARIFICATION_ANSWERED
  → ControlJobWorker submits TaskRunResumeExecutor
  → RuntimeExecutionService.completeRecoveredClarificationAction
  → Observation/Evaluation continues
```

`WAITING_HUMAN` 不能由“没有 READY Task”反推，必须由 `clarification_request` 或授权请求等显式对象解释。

## 持久化 Worker

```text
TaskCoordinator
  → create one or more TaskRun + Dispatch in one transaction
  → TaskRunWorker claims Dispatch with SKIP LOCKED / conditional update
  → acquire TaskRun lease
  → LoopExecutionCoordinator
  → checkpoint/event/outbox
```

Worker 崩溃后租约过期，由恢复扫描器根据 Checkpoint 与副作用分类决定自动续跑、对账或人工处理。

v0.1 已提供本机 `RecoveryWorker`：定时扫描 lease 过期或 `WAITING_CHILD_JOB` 的可恢复 TaskRun，
再委托 `TaskRunResumeExecutor` 进行策略判定、租约竞争和审计。它不是最终分布式队列，
后续 Outbox/队列 Worker 可以替换该入口。

同一 Job 可以并行运行多个无依赖 READY Task。Task 完成事务必须锁定 Job，幂等完成当前 Task、
批量提升依赖已满足的后继 Task，并仅由最后一个完成者提交 Job 终态。

## Child Job 创建

```text
LoopNode produces ChildJobRequest
  → persist request checkpoint
  → JobCoordinator locks parent/root counters
  → validate policy, budget and recursion limits
  → idempotently create Child Job + TaskGraph + job_derivation
  → parent LoopNode/TaskRun = WAITING_CHILD_JOB
  → emit CHILD_JOB_CREATED
```

## Child Job 完成回传

```text
Child Job passes JobCompletionPolicy
  → write ChildJobOutcome
  → emit CHILD_JOB_COMPLETED
  → recovery worker claims parent TaskRun
  → reacquire lease
  → inject outcome as Loop Action Result
  → continue Observation/Evaluation
```

若子 Job 已完成但父进程未恢复，事件重放必须得到同一结果，不重复创建子 Job或重复消费 Outcome。

`CHILD_JOB_CREATED` 和 `CHILD_JOB_COMPLETED` 均是正式恢复游标。父恢复先检查
`job_derivation.outcome_json`，再重新获取 origin TaskRun 租约；Outcome 消费必须幂等。

## 暂停与取消

- 暂停父 Job：阻止新的子 Job 与 TaskRun 派发，正在安全点运行的子树进入 PAUSED。
- 取消父 Job：向所有非终态阻塞型子 Job 传播取消。
- 子 Job 失败：父 Loop 收到失败 Outcome，由本层策略决定重试、调整或失败。

## Checkpoint

关键类型包括 `RUN_START`、`ACTION_PREPARED`、`CHILD_LOOP_CREATED`、`CHILD_JOB_CREATED`、`ACTION_COMPLETED`。Checkpoint 必须保存安全恢复所需的策略哈希、作用域、动作指纹和事件 offset。

## Clarification 与 WAITING_HUMAN

`WAITING_HUMAN` 只表示执行点暂停，原因必须由 `clarification_request` 或授权请求表达。

```text
Tool/Loop/TaskGraph detects missing input
  → create ClarificationRequest
  → Task/TaskRun/LoopNode = WAITING_HUMAN
  → Agent Path shows request and question
  → user answer binds to original source
  → RecoveryWorker or command handler resumes original point
```

禁止通过 `status != READY` 反推出“需要补充信息”。

用户新消息不会自动回答最近的等待项。Control 先读取 Conversation 下所有 open
ClarificationRequest，交给 PendingInteractionMatcher 判断：

```text
User Message
  + Open Clarification candidates
  → ANSWER_CLARIFICATION: bind answer + assess structured contract
      → incomplete: keep ClarificationRequest OPEN, record partial facts
      → complete: mark ANSWERED/RESOLVED + resume original target
  → NEW_INTENT: create / route independent job
  → AMBIGUOUS: ask user to disambiguate target
```

Loop 执行中如果模型返回的是“请补充信息”而非结果，`ClarificationNeedDetector`
必须将其正式化为 `clarification.request`，禁止 Job 因非空文本进入 `COMPLETED`。
检测器必须区分“缺失关键输入的追问”和“最终结果后的可选追问”，避免把已完成产物误开成新的
ClarificationRequest。
