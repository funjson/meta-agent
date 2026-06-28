# Runtime 模块

## 职责与非职责

- 提供跨层中立合同、AuthorityEnvelope、预算、策略继承与派生请求。
- 不拥有 Job/Task/Loop 协调器或数据库业务流程。

## 类图

```mermaid
classDiagram
  PolicyResolver --> PolicyLayer
  PolicyResolver --> EffectivePolicy
  ChildJobRequest --> TaskGraphTemplateRef
  ChildJobRequest --> ContractContribution
  ChildJobRequest --> CapabilityRequest
```

## 核心流程

父级策略 + 子级请求 → 权限交集、预算封顶、合同加强 → ALLOWED / WAITING_APPROVAL / WAITING_HUMAN / REJECTED。

## 类与功能关系

- `PolicyResolver`：统一有效策略计算。
- `AuthorityEnvelope`：只能收窄的权限。
- `ChildJobRequest/Outcome`：Loop 与 Job 的中立交接。
- `RuntimeStateException`：跨层稳定错误合同。

## 所有权与依赖

Runtime 不依赖任何业务上层模块。

## 扩展点与测试入口

扩展成本预算、数据边界和授权策略；入口为 `PolicyResolverTest` 与 ArchUnit。

