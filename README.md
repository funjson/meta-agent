# Meta Agent Framework

通用、可恢复、可观测的 Agent 任务执行框架。

## 架构

- 架构治理入口：`架构设计/README.md`
- 正式架构包：`ssf-workspace/instances/SPI-001/architecture-spec/`

## 本地前置条件

- Java 21
- Maven 3.8.5+
- Node.js 24+
- Docker

项目自带 `.mvn/settings.xml`，强制使用 Maven Central，不依赖用户全局私有仓库配置。

## 启动 MySQL

```powershell
Copy-Item .env.example .env
docker compose up -d mysql
```

数据库只监听 `127.0.0.1:3307`，不会占用本机已有的 3306。

## 启动后端

```powershell
mvn spring-boot:run -pl backend
```

- API：`http://127.0.0.1:8080`
- Health：`http://127.0.0.1:8080/actuator/health`
- System info：`http://127.0.0.1:8080/api/v1/system/info`

## 启动前端

```powershell
Set-Location frontend
npm install
npm run dev
```

前端：`http://127.0.0.1:5173`

## Secret

真实 DeepSeek Key 仅通过系统环境变量或后续设置页面输入。不要写入 `.env`、源码或提交记录。

## 当前实现进度

- S0：工程、本地 MySQL、Flyway、后端健康检查、React 页面已完成。
- S1：Job 创建、首个 READY Task、RuntimeEvent、Outbox、幂等命令和浏览器列表已完成。
- S2：Fake Provider、TaskRun、LoopRun、LoopNode、Checkpoint、Evidence 和运行详情已完成。
- S3：DeepSeek Provider、环境变量/内存 Secret、页面配置测试、真实 Loop 和 model_call 审计已完成。
- S4：安全 Checkpoint 判定、普通 Loop 与 Workflow Stage 崩溃恢复、
  租约和恢复审计已完成；下一步补充 UNKNOWN 副作用对账、暂停与取消传播。
