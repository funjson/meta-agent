# 04 Data Model

## 实现校准 r7

- `clarification_request` 新增 `contract_json`、`answer_message_id`、`resolution_json`、`resolved_at`，用于把用户可见问题、系统结构化合同、用户回答、结构化抽取事实和缺失字段绑定回原始恢复点。
- 新增 `tool_invocation`，记录 Tool ID、Tool 类型、幂等键、参数、状态、结果、错误、关联 Job/Task/TaskRun/LoopRun/LoopNode，以及可选 `clarification_request_id`。
- `loop_node.action_type` 已持久化，用于 Agent Path、恢复游标和 Tool/Skill/Clarification 执行路径展示。
- `checkpoint` 正式使用 `CLARIFICATION_ANSWERED` 表示用户回答已落库且原 TaskRun 可恢复；`CLARIFICATION_REQUESTED` 是等待人工的审计安全点，不自动续跑。

所有核心表使用稳定 UUID、UTC 时间、乐观锁和可审计状态迁移。

## 核心表

| 表 | 关键字段 |
|---|---|
| conversation | id, profile_id, title, status, active_job_id, version |
| message | id, conversation_id, role, content, job_id, task_run_id |
| control_turn | id, conversation_id, source_message_id, idempotency_key, status, job_id, version |
| control_decision | id, control_turn_id, intent_json, decision_summary |
| agent_profile | id, name, policy_json, runtime_json, version |
| task_graph_template | id, profile_id, template_key, version, graph_json, checksum, status |
| job | id, profile_id, parent_job_id, root_job_id, recursion_depth, template_id, template_version, effective_policy_snapshot, effective_policy_hash, status |
| task | id, job_id, task_key, sequence_no, goal, status, contract_json, active_task_run_id |
| task_dependency | task_id, depends_on_task_id, dependency_type |
| task_run | id, task_id, attempt_no, status, lease_owner, lease_until, latest_checkpoint_id, result_summary |
| loop_run | id, task_run_id, status, root_node_id, policy_json, scoped_context_json |
| loop_node | id, loop_run_id, parent_node_id, status, current_phase, active_child_job_id, input_json, output_json |
| loop_node_phase | id, loop_node_id, sequence_no, phase_type, status, input_json, output_json |
| clarification_request | id, conversation_id, job_id, task_id, task_run_id, loop_node_id, question, contract_json, answer, resolution_json, status |
| web_search_run | id, tool_invocation_id, job_id, task_id, task_run_id, loop_run_id, loop_node_id, query_text, recency_days, domains_json, locale, result_count |
| web_search_candidate | id, search_run_id, tool_invocation_id, job_id, task_id, task_run_id, loop_run_id, loop_node_id, rank_no, title, url, snippet, provider, source_type, published_at |
| web_source_document | id, tool_invocation_id, job_id, task_id, task_run_id, loop_run_id, loop_node_id, url, title, source_type, content_hash, text_excerpt, fetched_at |
| web_evidence_item | id, source_document_id, tool_invocation_id, job_id, task_id, task_run_id, loop_run_id, loop_node_id, rank_no, excerpt, relevance_score, source_type |

## 派生与授权

| 表 | 关键字段 |
|---|---|
| job_derivation | id, parent_job_id, child_job_id, origin_loop_node_id, idempotency_key, request_json, outcome_json, status |
| authorization_request | id, job_id, task_run_id, loop_node_id, request_type, requested_delta_json, status, decision_json |
| capability_source | id, version, raw_content, compiled_manifest_json, checksum, prompt_version, content_hash, status |
| capability_load | id, loop_node_id, source_id, source_version, scope_root_id, inherited_from_load_id |
| skill_package | id, version, name, manifest_checksum, status |
| skill_resource | package_id, package_version, path, resource_type, content_hash, executable_json |
| subagent_profile | id, agent_profile_id, name, role_prompt, model_policy_json, skill_refs_json, tool_allowlist_json, authority_json, version, status |

## 运行保障

| 表 | 用途 |
|---|---|
| task_run_dispatch | MySQL 持久化 Worker 队列 |
| checkpoint | 安全恢复游标；新增 `CHILD_JOB_CREATED` 类型 |
| runtime_event | 可重放运行事件 |
| outbox_event | 状态与外部发布同事务 |
| evidence | Loop/Task/Job 验收证据 |
| recovery_attempt | 恢复决定与执行审计 |
| model_call / tool_call | 外部调用审计 |
| web_search_run / web_search_candidate | Web Research 搜索运行和候选池；挂回 ToolInvocation 与 LoopNode |
| web_source_document / web_evidence_item | Web Research 已读取来源和证据池；挂回 ToolInvocation 与 LoopNode |
| evaluation_run | Agent 评测事实 |

## 数据不变量

1. 根 Job 的 `root_job_id=id`，子 Job 继承相同 root。
2. `job_derivation.idempotency_key` 全局唯一。
3. 子 Job 深度、直接数量和整树数量在创建事务中锁定并校验。
4. 父 LoopNode 与 TaskRun 等待同一 `child_job_id`。
5. `ChildJobOutcome` 写入派生记录后再发恢复事件。
6. 所有路径投影均可由事实表重建。
7. 旧流程运行表通过新 Flyway 迁移删除，本地开发库重建，不做兼容读取。
8. `control_turn.source_message_id` 与 `idempotency_key` 均唯一。
9. 同一 READY Task 只能存在一个非终态 Dispatch；并行完成时必须先锁 Job 再推进依赖。
10. `skill_resource.path` 必须是包内相对路径，禁止 `..`、绝对路径和符号链接逃逸。
11. SubagentProfile 生效策略只能等于或窄于父 Job 的 EffectivePolicy。
12. `web.search` 结果不是证据，只进入 `web_search_run` / `web_search_candidate`；
    只有 `web.fetch` / `web.extract` 实际读取过的来源才进入 `web_source_document`，
    只有从来源正文抽取的片段才进入 `web_evidence_item`。
