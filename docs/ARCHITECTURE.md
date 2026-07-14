# GD Trade 项目架构

## 技术栈

- Kotlin。
- Jetpack Compose。
- Android 原生开发。
- ViewModel + StateFlow。
- Room Database。

## 架构模式

项目采用 MVVM 与 Repository Pattern，并按 Domain、Data、UI 分层维护。

### UI 层

- Compose 负责页面展示和用户交互。
- ViewModel 负责组合仓储 Flow、处理界面事件并暴露 StateFlow。
- UI 不直接访问数据库、网络接口或风险规则实现。

### Domain 层

- 维护 Position、TradeRecord、StockCandidate、MarketQuote 等既有领域模型。
- V1.2 新增 `market-domain-v1` 行情领域契约，位于 `domain/model/market/**`，统一描述行情值、数据状态、来源、请求、快照、错误和刷新结果。
- `MarketDataStatus` 固定为 SUCCESS、LOADING、ERROR、DELAYED、MOCK；静态或 Mock 数据不得标记为实时。
- RiskEngine 负责风险决策，不依赖 Android UI 或具体数据来源。
- Repository 接口作为 Domain/UI 与 Data 实现之间的边界。
- 新 `domain/repository/MarketDataRepository` 声明单股观察、批量观察和批量刷新端口，由 Data 层 `DefaultMarketDataRepository` 实现。
- `domain/usecase/market/**` 负责从持仓和观察池提取、标准化、去重股票代码，构建显式行情请求，并在组合结果中恢复原列表顺序。
- 行情 UseCase 保留完整 `MarketDataState`、`QuoteSnapshot`、错误、缺失代码、完整度及逐只状态和来源；不得把 LOADING、ERROR、DELAYED 或 MOCK 压平为普通成功列表。
- UseCase 行情质量分为研究输入、仅观察、仅测试和不可用；该分级只描述研究用途，不能替代 RiskEngine，也不生成可执行买卖结论。
- 旧 `MarketRepository` 与旧 `MarketQuote` 在 V1.2 迁移期间保持签名不变，通过 `StockQuote -> MarketQuote` 单向适配继续服务现有 Dashboard。

### Data 层

- Repository 实现负责协调 Room、本地迁移和行情数据源。
- Room Entity、DAO、Database 与领域模型映射独立维护。
- `RoomTradeRepository` 的持仓、交易记录和观察池读写只依赖 `PortfolioLocalDataSource` 与 `WatchlistLocalDataSource`，不直接访问 DAO。
- `RoomLocalDataSource` 是本地数据唯一 DAO 访问入口，并通过 `LegacyMigrationCoordinator` 在公开读写前保证 V1.1 旧数据迁移完成；本轮未修改 Room Schema。
- `TencentRemoteMarketDataSource` 负责腾讯 HTTP 请求，并复用 `QuoteParser` 与 `QuoteMapper` 完成协议解析和 Domain 映射；当前腾讯来源固定为 `TENCENT_QT`、`supportsRealtime=false` 和 `DELAYED`。
- `DefaultMarketDataRepository` 是行情组合入口，依次协调 `RemoteMarketDataSource`、`QuoteMemoryStore` 与 `FallbackMarketDataSource`，不读取 Room、Position 或 StockCandidate。
- `QuoteMemoryStore` 只保存进程内最后报价及成功接收时间，不改变 `StockQuote.dataStatus` 和 `source`；ERROR/LOADING 不入缓存，Mock 不覆盖已有远程有效报价。
- 远程批量部分缺失时只对缺失代码调用 fallback；fallback 固定为 `MOCK`、非实时和 `FALLBACK` 来源，不覆盖远程成功结果。
- 批次状态按 LOADING、无可用数据时 ERROR、任一 MOCK、任一 DELAYED、全部 SUCCESS 的顺序汇总，并通过 `QuoteSnapshot` 保留请求代码、结果、缺失代码和完整度。
- `RoomTradeRepository` 同时暴露旧 `MarketRepository` 与新 `MarketDataRepository`，两条路径委托同一个 `DefaultMarketDataRepository`；同批短窗口刷新（包括失败结果）由组合层复用，避免重复网络请求。
- `StockQuoteLegacyAdapter` 只执行 `StockQuote -> MarketQuote` 映射；旧 `isRealtime` 必须同时满足 SUCCESS、来源支持实时、存在更新时间且更新时间满足新鲜度要求。
- 行情接口不可用时可以回退静态样例，但必须明确标注为非实时行情。
- V1.2 Repository 组合层仍只使用内存行情缓存，未修改 Room Entity、DAO、Database version、Schema 或 Migration。

## V1.2 行情基础层集成状态

- 集成分支：`integration/v1.2-market`。
- 已集成：LocalDataSource、`market-domain-v1`、QA fixture 与生产契约、Remote DTO/Parser/Mapper/Error、Repository 组合、缓存、fallback 和旧接口适配。
- 行情 UseCase 已从 `origin/codex/usecase-market-v1-2` 保留完整提交历史合入 `integration/v1.2-market`；真实 QA UseCase 契约已由生产 UseCase 激活并通过完整回归。
- 未集成且继续冻结：DashboardViewModel 迁移、Compose UI、股票评分、AI 日报、推送提醒和自动交易。
- 架构边界复核结果：`market-domain-v1` 模型与富行情端口不依赖 Data；UseCase 仅依赖现有 `data.repository` 包中的 `PortfolioRepository`、`MarketRepository` 接口，不依赖其实现、DAO 或 Remote；Remote DTO 未泄漏；旧、新行情接口共享同一 `DefaultMarketDataRepository`。

## V1.2 行情 UseCase 层

```text
PortfolioRepository.observePositions()
    -> GetPortfolioQuotesUseCase
        -> QuoteRequest(reason=PORTFOLIO)
            -> MarketDataRepository.observeQuotes()

MarketRepository.observeCandidates()
    -> GetWatchlistQuotesUseCase
        -> QuoteRequest(reason=WATCHLIST)
            -> MarketDataRepository.observeQuotes()

显式单股代码
    -> GetStockDetailUseCase
        -> SingleQuoteRequest(reason=STOCK_DETAIL)
            -> MarketDataRepository.observeQuote()
```

- 标准代码为六位数字；UseCase 可清洗首尾空白和常见 `sh`/`sz` 输入前缀，传给 Repository 的代码始终显式、标准化且去重。
- Repository 的 `Set` 不承担展示顺序；持仓和观察池输出按原始列表顺序组合，重复项仍映射到同一标准报价。
- 空持仓和空观察池输出 `QuoteCollectionState.EMPTY`，不调用 `MarketDataRepository`；仅含非法代码时输出明确领域错误，同样不发起请求。
- `GetStockDetailUseCase` 返回包含原始 `MarketDataState<StockQuote>` 的结果，SUCCESS、LOADING、ERROR、DELAYED、MOCK 及错误对象均不丢失。
- `GetMarketOverviewUseCase` 当前固定返回 `InsufficientData`，明确缺少全市场覆盖、板块和资金流能力，禁止用持仓或观察池推断全市场结论。
- `MarketDataUsage.RESEARCH_INPUT` 只允许进入后续候选研究；任何候选结论仍须经过 RiskEngine，RiskEngine 的否决不可被行情质量、评分、AI 或 UI 覆盖。

## RiskEngine 设计原则

- 风险否决优先于研究型买入信号。
- 风险引擎输出只用于研究辅助和提醒，不触发自动交易。
- 每条风险规则应可独立测试，并说明触发条件和否决原因。
- 未满足数据完整性或时效性要求时，应采取保守结论。
- 最终证券买卖行为必须由用户在外部行情或交易软件中人工确认。
