# 16 Weather Tool, Current Time Context, and Output Budget

## 背景

本轮修复两个生产化问题：

1. Deep Research / 结构化报告最终输出可能被 provider `max_tokens` 截断，但 Loop 仍按“非空文本”完成。
2. 用户询问“北京天气如何”等强时效问题时，系统缺少天气专用工具，模型可能把查询改写成带错误年份的 `web.search`。

## 当前时间上下文

新增 `runtime` 中立事实：

- `CurrentTimeContext`
- `CurrentTimeContextProvider`

它不作为聊天消息持久化，也不伪造成用户上下文，而是作为系统事实注入：

- Control intent prompt：辅助意图识别和软标签，不允许凭空引入旧年份。
- Loop context：以 `Current Time` 约束块进入 `LoopContextSnapshot`，供 function calling 参数生成、fallback planner 和最终回答使用。

规则：

- “今天 / 现在 / 当前 / 最新 / 近期 / 今年”必须以 `Current Time` 为准。
- 用户显式给出的绝对日期、月份、年份或业务时间范围优先级最高；`Current Time` 只用于解释相对时间和判断时效边界。
- 如果用户给出明显异常的时间，例如年份位数异常，系统不应擅自修正；应保留原始表达或请求用户确认。
- 模型不得凭空给查询或结论添加其他年份。
- 天气、新闻、价格、政策、版本等强时效问题应显式参考当前时间。

## Weather Tool

新增顶层模块 `weather`：

```text
ToolCatalogService
  → weather.current
      → ToolExecutionService
          → WeatherService
              → WeatherClient
                  → OpenMeteoWeatherClient
```

`weather.current` 参数：

- `location`：必填，城市或地点。
- `forecastDays`：可选，1 到 7 天。
- `locale`：可选，默认 `zh-CN`。

默认 provider adapter 使用 Open-Meteo：

1. Geocoding API 将自然语言地点解析为经纬度。
2. Forecast API 查询 current weather 和 daily forecast。
3. Tool 返回结构化 Observation，最终用户回答仍由 Loop 的下一轮 `MODEL_CALL` 合成。

重要边界：

- 天气不是 Deep Research 的网页证据链，不进入 `web_search_run` / `web_evidence_item`。
- 天气强实时问题优先使用 `weather.current`，不要用 `web.search` 或网页抽取替代。
- 后续接入商业天气供应商时，只新增 `WeatherClient` adapter，不改变上层 Tool 合同。

## Query Rewrite / Tool Argument Generation

当前系统不再把天气问题改写成搜索串，而是让模型通过 function calling 生成结构化参数。

错误路径：

```text
用户：北京天气如何
  → web.search(query="北京今天天气预报 2025")
```

目标路径：

```text
用户：北京天气如何
  → weather.current(location="北京", forecastDays=3, locale="zh-CN")
  → Observation
  → MODEL_CALL 合成用户可见回答
```

对通用 Web Search 仍保留查询改写能力，但 prompt 约束为：

- 保留用户实体、时间和限定条件。
- 只使用 `Current Time` 解释相对时间。
- 不虚构年份。

## Output Budget and Truncation Completion Policy

`RuntimeExecutionService` 不再为所有原生 tool calling 的 `MODEL_CALL` 固定使用 1024 tokens：

- 默认回答：1024。
- 长文本 / 分析 / 报告：2048。
- Deep Research / 研究报告 / 证据矩阵 / 报告合成 / 质量复核：4096。

`LoopEvaluator` 新增 provider 截断判断：

- 如果 `LoopActionResult.attributes.finishReason == "length"`，不能完成。
- 若 LoopTree 仍有预算，进入 `ADJUST`，要求模型生成“完整但更紧凑”的最终版本。
- 若预算耗尽，进入 `FAIL`，避免把半截报告写入最终聊天消息。

## Prompt 更新

- `control.intent-recognition.v1`：新增 `currentTime` 变量。
- `loop.execution.v2`：新增天气工具、当前时间和截断恢复规则。
- `loop.action-planning.v1`：fallback planner 也遵守 `weather.current` 和 `Current Time` 规则。

## 测试入口

- `LoopEvaluatorTest.adjustsWhenModelOutputWasTruncatedByTokenLimit`
- `LoopContextBuilderWebResearchTest.injectsCurrentTimeAsConstraint`
- `ToolExecutionServiceWeatherTest.weatherCurrentReturnsStructuredObservation`
- `OpenMeteoWeatherClientTest.parsesGeocodingAndForecastResponses`

## 验收建议

1. 问“北京天气如何”，Agent Path 应出现 `weather.current`，不应出现 `web.search(query=...2025...)`。
2. 问“明天上海天气怎么样”，应使用当前时间推断“明天”，并调用 weather tool。
3. 跑 Deep Research 报告类任务，若 provider 返回 `finishReason=length`，系统不应直接完成 Job；应调整生成完整压缩版或失败。
4. Agent Path 中天气工具只展示调用和结果摘要，不把天气结果伪造成 Web Evidence。
