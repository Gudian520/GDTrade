# 任务完成记录

## 1. 任务名称

完成 GD Trade V1.2 行情系统 UseCase 层。

- 完成日期：2026-07-14。
- 负责角色：GD UseCase Developer Agent。
- 实施范围：持仓行情、观察池行情、单股详情、市场概览、行情质量解释与真实 QA UseCase 契约激活。

## 2. 分支与提交

- 基线分支：`integration/v1.2-market`。
- 基线 commit：`221f6c4dd9eb588640a352dd528f975e0f4de5a7`。
- 远端核验：`origin/integration/v1.2-market` 为同一提交，基线最新性已确认。
- 当前开发分支：`codex/usecase-market-v1-2`。
- 最终功能 commit：`5bd3062942478d376182ec1ab466eaedfc261903`。
- 完成记录 commit：以包含本记录回填内容的分支 HEAD 为准。

## 3. 完成内容

- 实现 `GetPortfolioQuotesUseCase`：监听持仓、标准化和去重代码、构造 `PORTFOLIO` 显式请求，并按原持仓顺序组合报价。
- 实现 `GetWatchlistQuotesUseCase`：监听观察池、标准化和去重代码、构造 `WATCHLIST` 显式请求，并按原观察池顺序组合报价。
- 实现 `GetStockDetailUseCase`：校验并标准化显式单股代码，构造 `STOCK_DETAIL` 请求，完整保留单股 `MarketDataState`。
- 实现 `GetMarketOverviewUseCase`：在缺少全市场、板块和资金流能力时明确输出 `InsufficientData`，不使用持仓或观察池伪造市场整体结论。
- 新增 UseCase 输出模型、代码标准化器与行情质量解释器。
- 激活真实 `MarketUseCaseContractTest`，并使用真实 UseCase 与现有 RiskEngine 验证安全边界。

## 4. 新增文件

### 生产代码

- `app/src/main/java/com/gudian/gdtrade/domain/usecase/market/GetPortfolioQuotesUseCase.kt`
- `app/src/main/java/com/gudian/gdtrade/domain/usecase/market/GetWatchlistQuotesUseCase.kt`
- `app/src/main/java/com/gudian/gdtrade/domain/usecase/market/GetStockDetailUseCase.kt`
- `app/src/main/java/com/gudian/gdtrade/domain/usecase/market/GetMarketOverviewUseCase.kt`
- `app/src/main/java/com/gudian/gdtrade/domain/usecase/market/MarketUseCaseModels.kt`
- `app/src/main/java/com/gudian/gdtrade/domain/usecase/market/MarketSymbolNormalizer.kt`
- `app/src/main/java/com/gudian/gdtrade/domain/usecase/market/MarketDataQualityInterpreter.kt`

### 测试代码

- `app/src/test/java/com/gudian/gdtrade/domain/usecase/market/FakeMarketRepositories.kt`
- `app/src/test/java/com/gudian/gdtrade/domain/usecase/market/MarketUseCaseFixtures.kt`
- `app/src/test/java/com/gudian/gdtrade/domain/usecase/market/MarketUseCasesTest.kt`
- `app/src/test/java/com/gudian/gdtrade/domain/usecase/market/RealMarketUseCaseContractTest.kt`

## 5. 修改文件

- `docs/TASK_QUEUE.md`
- `docs/ARCHITECTURE.md`
- `CHANGELOG.md`
- `TASK_COMPLETION.md`

未修改 `DashboardViewModel`、Compose UI、Room、Remote、Repository 实现、RiskEngine 或自动交易逻辑。

## 6. UseCase 数据流

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

- 传给行情仓储的代码始终为标准六位数字、显式集合并按首次出现顺序去重。
- `Set` 不承担 UI 顺序；UseCase 使用原持仓或观察池列表恢复组合顺序。
- 重复代码只请求一次，但原始列表中的每一项仍可映射到同一报价。

## 7. 空集合与非法代码处理

- 空持仓输出 `QuoteCollectionState.EMPTY`，不调用 `MarketDataRepository`，也不使用 `observeQuotes(emptySet())`。
- 空观察池同样输出明确空状态且不调用行情仓储。
- 仅含非法代码时输出 `INVALID_SYMBOLS_ONLY` 与结构化领域错误，不向冻结仓储传入非法或空请求。
- 单股详情代码非法时直接输出 `MarketDataState(ERROR)`，并在 UseCase 输出中保留原始输入，不调用行情仓储。

## 8. 状态、错误与行情质量传播

- `PortfolioQuotesResult` 与 `WatchlistQuotesResult` 保留原始 `MarketDataState<QuoteSnapshot>`。
- `StockDetailResult` 保留原始 `MarketDataState<StockQuote>`。
- SUCCESS、LOADING、ERROR、DELAYED、MOCK 不被压平或替换。
- `MarketDataError`、`missingSymbols`、`DataCompleteness`、每只 `StockQuote.dataStatus`、每只 `StockQuote.source` 均可从输出直接取得。
- `DELAYED` 归类为仅观察，`MOCK` 归类为仅测试，`ERROR` 归类为不可用；三者均不支持研究结论。
- `SUCCESS` 只表示可作为后续研究输入，不自动等于实时；非实时来源通过质量原因继续保留提示。
- UseCase 不伪造 `isRealtime`、`updatedAt` 或数据来源。

## 9. QA UseCase 契约激活情况

- 新增 `RealMarketUseCaseContractTest` 继承既有抽象 `MarketUseCaseContractTest`。
- 契约使用真实 `GetPortfolioQuotesUseCase`，不是在测试适配器中重新实现空集合、状态或质量规则。
- 风险场景组合真实 UseCase 输出与现有 RiskEngine，验证研究输入不能覆盖 RiskEngine deny。
- 新增 15 项测试：11 项 UseCase/Fake Repository 测试与 4 项真实 QA 契约测试。
- 覆盖空持仓、空观察池、Flow 变化、代码标准化去重、持仓顺序、观察池顺序、五种状态、错误、缺失代码、完整度、来源、非法代码、市场概览能力不足、ERROR/MOCK 研究边界和 RiskEngine 否决优先。

## 10. 测试与构建结果

执行：

```text
gradlew.bat test --no-daemon
gradlew.bat assembleDebug --no-daemon
git diff --check
```

结果：

- Debug 单元测试：88 项通过，失败 0，错误 0，跳过 0。
- Release 单元测试：88 项通过，失败 0，错误 0，跳过 0。
- 其中新增 UseCase 与真实契约测试：每个变体 15 项通过。
- Debug APK 构建：通过。
- APK：`app/build/outputs/apk/debug/app-debug.apk`。
- APK 大小：9,111,704 字节。
- APK SHA-256：`37FCC097286A1FB3F032ECFE91A9671F7885A7F1E0E8BDAB230A679E1D20E627`。
- `git diff --check`：通过。

首次直接执行 Wrapper 时，沙箱用户目录未命中本机已有 Gradle 8.10.2 缓存并进入下载，120 秒后超时。随后显式复用本机同版本缓存，通过项目 Wrapper 完成全量测试与构建；该问题不属于产品代码失败。

## 11. 架构、兼容性与迁移影响

- 架构影响：新增 Domain UseCase 编排层，ViewModel 后续可以只消费 UseCase 输出，不再解释空列表语义或直接组织富行情仓储调用。
- 既有接口包路径：`PortfolioRepository` 与 `MarketRepository` 仍位于历史 `data.repository` 包；UseCase 只依赖接口，不依赖 Data 实现、DAO 或 Remote，本任务未迁移冻结接口。
- 兼容性：现有 `PortfolioRepository`、`MarketRepository`、`MarketDataRepository`、Dashboard 状态和旧行情适配路径均保持不变。
- 数据模型：未修改冻结的 `domain/model/market/**`。
- Repository：未修改任何接口签名，也未修改 `DefaultMarketDataRepository` 核心组合语义。
- Room：无影响；未修改 Entity、DAO、Database version、Schema 或 Migration。
- 风险：未修改 RiskEngine；研究用途分级不能覆盖其最终否决。
- 凭据：不保存证券账户密码、短信验证码、Cookie 或任何交易登录凭据。

## 12. 已知问题

- 腾讯供应商授权、稳定性、字段口径和真实时效能力仍未完成生产验收，继续按 DELAYED 或更保守状态处理。
- 行情缓存仍仅在进程内，应用重启后重新请求。
- 当前没有完整 A 股全市场、可靠板块或资金流能力，因此市场概览只能返回数据不足。
- 本阶段没有接入 DashboardViewModel；UI 尚不会展示新的结构化状态与质量分级。
- V1.1 旧版真机原位升级验收、V1.2 供应商联调和弱网/并发/前后台专项仍是独立发布门禁。
- `PortfolioRepository` 与 `MarketRepository` 的历史包路径仍是架构债务；如需迁移到 Domain 端口，应由 Architect 另立兼容任务，不应在 Dashboard 接入阶段顺带变更。

## 13. 冻结接口确认

- 是否修改 `domain/model/market/**`：否。
- 是否修改 `MarketDataRepository` 接口：否。
- 是否修改 `PortfolioRepository` 或 `MarketRepository` 接口：否。
- 是否修改 `DefaultMarketDataRepository`：否。
- 是否修改 DashboardViewModel 或 Compose UI：否。
- 是否修改 RiskEngine：否。
- 是否实现股票评分、AI 推荐、推送或自动交易：否。

## 14. 下一步建议与最终判断

- V1.2 Market UseCase Layer：已完成。
- QA UseCase 契约：已使用真实 UseCase 激活并通过。
- 建议进入 DashboardViewModel Integration 阶段：是。
- 接入阶段必须保持 UI 布局不变，完整传递 LOADING、ERROR、DELAYED、MOCK、错误、缺失代码、完整度与来源，不得在 ViewModel 中重建 UseCase 规则。
- 股票评分、AI 日报、推送和自动交易继续不在下一阶段范围内。
