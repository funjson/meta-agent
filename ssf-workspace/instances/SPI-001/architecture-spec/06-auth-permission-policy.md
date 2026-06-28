# 06 Auth Permission Policy

## AuthorityEnvelope

每次执行携带不可变 `AuthorityEnvelope`：

- allowedProviders
- allowedTools
- dataScopes
- fileScopes
- networkPolicy
- sideEffectPolicy
- delegableCapabilities
- approvalRules

子层有效权限只能是父层权限与本层请求的交集。

## 决策

| 情况 | 结果 |
|---|---|
| 请求突破不可委托安全边界 | REJECTED |
| 请求可委托权限或额外预算 | WAITING_APPROVAL |
| 输入或验收合同冲突 | WAITING_HUMAN |
| 请求在 allowlist 与预算内 | ALLOWED |

Skill 不能改变安全策略。它只能补充合同，或产生 CapabilityRequest 等待授权。

## Secret

- `DEEP_SEEK_API_KEY` 可从环境变量读取。
- 页面允许用户配置 Provider Secret，但服务端只返回 configured/masked 状态。
- Secret 不进入 Prompt、Event、Checkpoint、Evidence、日志或 Agent Path。

