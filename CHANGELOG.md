# 更新日志

本项目遵循语义化版本记录。所有行情与分析能力仅用于研究辅助，不构成自动交易指令。

## [未发布]

### 新增

- 集成 LocalDataSource 能力边界，新增 `PortfolioLocalDataSource`、`WatchlistLocalDataSource`、`LegacyMigrationCoordinator` 与 `RoomLocalDataSource`。
- 实现 V1.2 `market-domain-v1` 行情领域契约，新增 `StockQuote`、行情状态、来源、请求、快照、错误、完整度和刷新结果模型。
- 新增 `MarketDataRepository` Domain 端口，支持单股票观察、批量观察和批量刷新。
- 新增领域契约、不变量和 Repository 端口测试。
- 新增 `TencentRemoteMarketDataSource`，复用既有 Parser/Mapper 并结构化处理 HTTP、网络、解析和字段错误。
- 新增 `QuoteMemoryStore`、`FallbackMarketDataSource` 和 `DefaultMarketDataRepository`，完成 Remote、进程内缓存与明确 Mock fallback 组合。
- 新增 Repository 组合与兼容测试，覆盖全部成功、全部失败、部分缺失、状态汇总、缓存保留和新旧接口刷新去重。
- 接入 V1.2 QA fixture、抽象契约模板和固定 Remote 输入，并将 StockQuote、腾讯 Parser/Mapper、DefaultMarketDataRepository 绑定为可执行生产契约测试。
- 新增 V1.2 行情基础层集成报告，记录实际分支、冲突处理、架构边界、重复请求专项和剩余发布门禁。
- 新增 `GetPortfolioQuotesUseCase`、`GetWatchlistQuotesUseCase`、`GetStockDetailUseCase` 与 `GetMarketOverviewUseCase`，完成 V1.2 行情业务编排层。
- 新增行情 UseCase 输出模型与质量分级，保留请求状态、错误、快照、缺失代码、完整度、逐只来源和逐只数据状态。
- 使用 Fake Portfolio、Watchlist 与 MarketData Repository 激活真实 `MarketUseCaseContractTest`，覆盖空集合、代码标准化去重、顺序、五种状态、完整度与 RiskEngine 否决边界。

### 变更

- `RoomTradeRepository` 移除内置腾讯 HTTP、协议解析和 fallback 逻辑，改为复用同一个 `DefaultMarketDataRepository`。
- 建立 `StockQuote -> MarketQuote` 单向兼容映射，旧接口签名、空列表跟随持仓语义和 Dashboard 装配保持可用。
- 新旧行情接口共享远程结果、内存缓存和短窗口同批刷新结果，成功及失败路径均不重复发起同一网络请求。
- `RoomTradeRepository` 的本地读写改为委托 LocalDataSource，DAO 与旧数据迁移职责集中到 `RoomLocalDataSource`。
- 持仓和观察池的代码提取、标准化、去重、空集合处理、顺序恢复与行情质量解释迁入 UseCase，后续 ViewModel 不再需要直接组织富行情 Repository 调用。

### 集成验证

- V1.2 Local、Domain、QA、Remote、Repository 基础层已集成到 `integration/v1.2-market`。
- Debug 与 Release 单元测试各 73 项通过，失败、错误和跳过均为 0。
- Debug APK 构建通过；重复网络请求专项覆盖新旧接口共享、同批短窗口共享、重叠批次差量请求和失败窗口后重试。
- UseCase 阶段 Debug 与 Release 单元测试各 88 项通过，失败、错误和跳过均为 0；Debug APK 构建通过。
- `git diff --check` 通过；RiskEngine、DashboardViewModel、Room Schema 和冻结的旧 Repository 接口未修改。

### 兼容性

- 现有 `MarketRepository`、`MarketQuote`、Room Schema、UI、ViewModel 和 RiskEngine 的公共契约保持不变。
- 未修改 Room Entity、DAO、Database version、Schema 或 Migration；行情缓存只存在于进程内。
- 未修改 Dashboard、Compose UI、评分、AI 分析、RiskEngine、Room 或自动证券交易；UseCase 可以独立交给下一阶段接入。

### 行情安全

- `MarketDataStatus` 仅允许 SUCCESS、LOADING、ERROR、DELAYED、MOCK 五种状态。
- Mock、静态样例和 fallback 来源不能声明实时能力；未知行情值使用 `null`，不伪造数值。
- 当前腾讯行情固定为 `DELAYED` 且 `supportsRealtime=false`；fallback 固定为 `MOCK`，不能伪装为 SUCCESS。
- ERROR 状态不具备生成有效交易信号的前提，后续业务层仍须经过 RiskEngine 否决检查。
- DELAYED 只用于观察研究，MOCK 只用于测试或明确占位，ERROR 与 MOCK 均不能支持有效研究结论；SUCCESS 仍不自动等于实时。
- 市场概览在缺少全市场、板块和资金流能力时明确返回数据不足，不使用少量持仓或观察池伪造市场整体判断。

## [1.5.0] - 2026-07-14

### 新增

- 新增 Room Database 本地持久化层。
- 新增 PositionEntity、TradeRecordEntity、StockCandidateEntity。
- 新增持仓、交易记录、观察池 DAO。
- 新增数据库 Schema 导出配置，便于后续版本编写 Room Schema Migration。
- 新增 SharedPreferences 到 Room 的一次性迁移器。
- 新增 Room DAO、旧数据导入、空列表迁移、幂等迁移和默认数据初始化测试。

### 变更

- 持仓、交易记录和动态观察池改由 Room 保存并通过 Flow 观察。
- PortfolioRepository 和 MarketRepository 接口保持不变。
- DashboardViewModel 和现有 Compose UI 逻辑保持不变。
- 原 LocalPreferenceRepository 类名保留为兼容入口，实际读写委托给 Room 仓储。
- App 版本号更新为 1.5.0，versionCode 更新为 2。

### 数据迁移

- 从旧版本升级时，首次访问仓储会读取 gd_trade_local_data 中的持仓、交易记录和观察池数据，并在同一数据库事务中写入 Room。
- 迁移成功后写入完成标记，后续启动不会重复覆盖 Room 数据。
- 旧 SharedPreferences 数据不会自动删除，保留用于升级回退和问题排查。
- 旧版本中用户主动清空的数据会按空列表迁移，不会重新填充默认样例。
- 全新安装且没有旧数据时，会初始化明确标注的 V1 静态样例；静态行情仍标注为非实时行情。

### 安全边界

- 不实现自动证券买卖。
- 不保存证券账户密码、短信验证码、Cookie 或交易登录凭据。
- Room 仅保存持仓、观察池和用户录入的交易记录。
- 风险引擎仍可优先否决研究型买入信号。
