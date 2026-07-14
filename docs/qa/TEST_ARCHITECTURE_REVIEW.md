# GD Trade 测试架构评审

## 评审范围

- 评审基线：`codex/qa-market-tests-v1-2`。
- 评审日期：2026-07-14。
- 重点：V1.2 行情领域模型、状态、Remote Parser、Repository 组合和 UseCase 的可测试性。
- 初始限制：QA 分支建立基线时 V1.2 领域模型和数据源尚未进入该分支，因此不伪造生产实现。
- 集成状态：`integration/v1.2-market` 已接入 Domain、Remote 与 Repository 生产实现，并按生产边界激活可执行契约。

## 当前已有测试

| 测试 | 已有能力 | 对 V1.2 的价值 |
| --- | --- | --- |
| `RiskEngineTest` | 风险否决优先、研究型买点边界 | 为行情评分和 AI 后续组合提供安全基线 |
| `RoomPersistenceTest` | Room 基础持久化和数据访问回归 | 保证 V1.2 行情拆分不应破坏 V1.1 本地数据基础 |

当前基线的主要缺口：

- 没有结构化 `StockQuote` 契约测试。
- 没有 `MarketDataStatus` 和来源的独立安全不变量测试。
- 没有固定远程响应 fixture 和 Parser 错误分类契约。
- 没有 Remote、Fallback 和内存快照的组合测试。
- 没有行情 UseCase 的状态传递和空请求测试。

## QA 分支初始新增测试

### 可立即执行

- `MarketFixtureTest`：验证正常、延迟、Mock、错误和加载 fixture，覆盖字段完整性、空值、时间、来源、实时标记和交易信号边界。
- `RemoteMarketFixtureTest`：验证正常响应、空响应、字段缺失和错误格式的 UTF-8 fixture 可被稳定读取。

### 初始抽象契约

- `StockQuoteContractTest`：完整字段、空值、未知更新时间和 Mock 来源。
- `MarketDataStatusContractTest`：五种状态、实时标记双条件和 ERROR 信号否决。
- `RemoteMarketParserContractTest`：四类原始响应的可分类解析结果。
- `MarketRepositoryContractTest`：远程成功、远程失败、部分成功和无回退错误。
- `MarketUseCaseContractTest`：空 symbol、延迟/Mock/Error 传递和 RiskEngine 否决不可覆盖。

抽象契约类不会在尚无生产实现时伪造“已覆盖”结果。Developer 实现对应类后，必须通过具体子类和薄适配器激活它们。

## 集成后的契约激活结果

| 契约 | 集成状态 | 说明 |
| --- | --- | --- |
| StockQuote | 已激活 | 直接创建并读取 `market-domain-v1` 生产模型 |
| MarketDataStatus | 等价生产测试覆盖 | 五种状态、实时前置条件、ERROR 与 Mock 安全边界由 Domain 和旧适配器测试覆盖；未在测试中伪造交易信号策略 |
| Remote Parser | 已激活 | 使用腾讯协议固定 fixture 调用生产 `QuoteParser` 与 `QuoteMapper` |
| Repository | 已激活 | 使用 Fake Remote/Fallback 驱动生产 `DefaultMarketDataRepository` |
| UseCase | 保持抽象 | UseCase 尚未实现，且基础层集成任务禁止新增 UseCase |

同时保留原始 JSON fixture 自检，未删除或放宽任何 Mock、延迟、错误和实时标识断言。

## Fixture 架构

```text
testFixtures/market/remote/
├── normal_response.json
├── empty_response.json
├── missing_fields.json
└── malformed_response.txt

app/src/test/java/com/gudian/gdtrade/market/fixtures/
├── MarketContractFixtures.kt
├── MarketFixtureTest.kt
├── RemoteMarketFixtureLoader.kt
└── RemoteMarketFixtureTest.kt
```

`testFixtures/` 当前是中立的固定输入目录，未为了引入 Gradle Test Fixtures 插件而修改构建配置。这避免与并行 Developer 的 Gradle 共享热点发生冲突。

## 未来测试

### Domain 已完成，后续补充

- 为真实 `StockQuote`、`MarketSourceInfo` 和 `MarketDataStatus` 提供契约适配器。
- 增加非法 symbol、负成交量、NaN/无穷价格和时间顺序边界。
- 测试 QuoteSnapshot 的 `COMPLETE`、`PARTIAL`、`EMPTY` 计算。

### Remote 已完成，后续补充

- 为真实 Parser 激活四类 fixture 契约。
- 增加 A 股/ETF 代码转换、非法数值、乱码、重复 symbol 和批量部分失败。
- 使用 Fake HTTP Client 覆盖非 2xx、连接超时和读取超时，不访问真实网络。

### Repository 已完成，后续补充

- 激活远程成功、明确 Mock 回退、部分成功和无数据 ERROR 契约。
- 增加请求去重、并发刷新、取消、缓存策略和新旧 Repository 共享快照。
- 验证 `observeQuotes(emptyList())` 现有兼容语义在迁移期不变。

### UseCase 和集成层实现后

- 激活空请求、状态传递和风险否决契约。
- 验证持仓变化更新 symbol 集合，观察池顺序和行情按代码组合。
- 回归 Dashboard 在 Loading 时保留旧快照，Error、Delayed 和 Mock 不丢失。
- 真机执行断网、弱网、恢复网络、长观察池和前后台切换。

## 并行开发评估

结论：**可以支持 Developer 并行开发，但需遵守文件所有权和契约激活门禁。**

- Domain Developer 可独立实现模型，QA 只在测试目录提供适配器。
- Remote Developer 可直接使用 `testFixtures/market/remote/` 的固定输入，不需要真实网络。
- Repository 和 UseCase Developer 可分别实现契约驱动器，无需共享 Fake 生产代码。
- QA 不修改 Repository 公共接口、Room、DashboardViewModel 或 Gradle 热点文件。
- 合并时应先引入架构确认的 Domain 契约，再接入具体测试子类，避免测试适配器反向定义生产语义。

## 当前剩余风险

- V1.2 架构方案仍需 GD Architect Agent 完成最终集成边界确认；`market-domain-v1` 已冻结且本次未修改。
- UseCase 抽象契约尚未绑定生产实现，不应将基础层通过解读为 UseCase、评分或 UI 已完成。
- 当前没有真实供应商时效和授权证据，新接入数据源默认不能声称实时。
- V1.1 真机旧版覆盖升级仍是独立发布门禁，不能被 V1.2 单元测试替代。
