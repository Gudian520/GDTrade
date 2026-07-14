# GD Trade 开发任务队列

## Agent Workflow Setup

- 状态：完成。
- 负责角色：GD Architect Agent。
- 已完成：Agent 角色、分支隔离、文件所有权、修改规则、测试门禁和标准开发流程。
- 产出：AGENTS.md、docs/DEVELOPMENT_WORKFLOW.md、docs/ARCHITECTURE_REVIEW.md。
- 下一阶段：V1.1 Room Database Migration。
- 状态说明：Room 主体代码已经完成，下一阶段重点是 GD QA Agent 执行真实设备迁移验收和工具链稳定性检查，不重复实现 Room。

## P0（最高优先级）

- [x] Room 数据库迁移。
- [x] 行情数据接口抽象。
- [ ] V1.1 Room 真实设备升级迁移验收。
  - 状态：待受控真机基线复测。
  - 负责角色：GD QA Agent。
  - 自动化状态：Room、Repository、ViewModel 回归测试及 Debug APK 构建均已通过。
  - 未完成条件：当前真机已是 `1.5.0/versionCode 2`，需准备旧版安装包和旧版数据后执行原位覆盖升级。
  - 验收方案：`docs/qa/V1_1_DEVICE_UPGRADE_TEST_PLAN.md`。

## V1.2 行情系统

### V1.2 行情基础层集成

- 状态：完成，待 Architect 最终边界确认与 QA 真机/供应商发布门禁。
- 负责角色：GD Integration Agent。
- 目标分支：`integration/v1.2-market`。
- 集成范围：LocalDataSource、`market-domain-v1`、QA 测试基线、Remote 行情层、Repository 组合层及旧接口兼容。
- 预期文件范围：上述各阶段既有生产代码与测试、`docs/integration/V1_2_MARKET_FOUNDATION_INTEGRATION_REPORT.md`、`docs/TASK_QUEUE.md`、`docs/ARCHITECTURE.md`、`CHANGELOG.md`、`TASK_COMPLETION.md`。
- 冻结范围：UseCase、`DashboardViewModel`、Compose UI、股票评分、AI 日报、推送提醒、RiskEngine、自动交易、Room Entity/DAO/Database/Schema/Migration。
- 集成门禁：按 Local、Domain、QA、Remote、Repository 顺序核对成果，完成架构边界、重复网络请求、完整单元测试、Debug 构建和 `git diff --check` 验证。
- 集成结果：Debug 73 项、Release 73 项单元测试全部通过，失败/错误/跳过均为 0；Debug APK 构建与 `git diff --check` 通过。
- 集成报告：`docs/integration/V1_2_MARKET_FOUNDATION_INTEGRATION_REPORT.md`。

### V1.2 行情架构设计

- 状态：进行中。
- 负责角色：GD Architect Agent。
- 当前进度：架构方案已输出，待接口评审与冻结。
- 文件范围：`docs/V1_2_MARKET_ARCHITECTURE_PLAN.md`、`docs/TASK_QUEUE.md`、`TASK_COMPLETION.md`。
- 兼容要求：现有 `PortfolioRepository`、`MarketRepository` 与 V1.1 数据迁移行为保持不变。
- 产出：行情领域模型、数据源边界、富行情端口、UseCase、文件所有权、迁移和测试方案。

### V1.2 行情领域模型契约

- 状态：完成。
- 负责角色：GD Architect Agent。
- 文件范围：`docs/V1_2_MARKET_DOMAIN_CONTRACT.md`、`docs/TASK_QUEUE.md`、`TASK_COMPLETION.md`。
- QA 对照：只读 `.agents/worktrees/qa-market-tests-v1-2/docs/qa/V1_2_MARKET_TEST_PLAN.md`，不修改 QA Agent 工作树。
- 冻结范围：StockQuote、MarketDataStatus、MarketSourceInfo、MarketDataRepository 文档契约。
- 产出：`docs/V1_2_MARKET_DOMAIN_CONTRACT.md`，契约编号 `market-domain-v1`。
- 下一阶段：GD Domain Developer 按冻结契约实现模型与新端口。
- 禁止范围：Kotlin 业务代码、现有 Repository、行情 API、Room、UI 和交易逻辑。

### V1.2 QA 测试基线

- 状态：完成并通过完整 Debug/Release 回归。
- 负责角色：GD QA Developer Agent；集成复核角色：GD Integration Agent。
- 来源分支与 worktree：`codex/qa-market-tests-v1-2`、`.agents/worktrees/qa-market-tests-v1-2`。
- 文件范围：`app/src/test/java/com/gudian/gdtrade/market/**`、`testFixtures/market/**`、`docs/qa/V1_2_MARKET_TEST_PLAN.md`、`docs/qa/TEST_ARCHITECTURE_REVIEW.md`。
- 已接入：固定行情 fixture、UTF-8 Remote fixture、StockQuote、MarketDataStatus、Remote Parser、Repository 和 UseCase 抽象契约模板。
- 已激活：StockQuote 生产模型、腾讯 Parser/Mapper、DefaultMarketDataRepository 组合契约；原有 Domain、Remote、Repository 生产测试继续保留。
- 暂不激活：UseCase 契约；原因是 V1.2 UseCase 尚未实现且本次集成禁止新增 UseCase。
- 安全规则：不得删除契约断言，不得放宽 Mock、DELAYED、ERROR、实时标识或 RiskEngine 否决规则。
- 验证结果：QA fixture 5 项、生产契约绑定 12 项在 Debug 与 Release 均通过；完整测试每个变体 73 项通过。

### V1.2 Developer 实现

- [ ] 阶段 0：现有行情、fallback 和兼容接口特征测试。
- [x] 阶段 1：抽取 LocalDataSource，不修改 Room Schema。
  - 状态：完成。
  - 负责角色：GD Developer Agent - LocalData Developer。
  - 文件范围：`data/local/datasource/**`、`RoomTradeRepository.kt`、`LocalDataSourceTest.kt`、任务文档。
  - 冻结范围：Repository 接口、DashboardViewModel、Compose UI、Room Entity/DAO/Database/Schema、腾讯行情和 RiskEngine。
- [x] 阶段 2：抽取 RemoteMarketDataSource、Parser 和静态 fallback。
  - 状态：完成。
  - 负责角色：GD Remote Data Developer Agent、GD Repository Developer Agent。
  - 完成范围：`data/remote/market/**`、`data/repository/FallbackMarketDataSource.kt`、对应 Remote 与 Repository 单元测试、任务记录。
  - 完成内容：RemoteQuoteDTO、腾讯 QuoteParser、QuoteMapper、RemoteMarketError、真实网络调用封装、结构化 HTTP/网络错误和明确 Mock fallback。
  - 冻结范围：Domain 行情模型与 `MarketDataRepository`、Room、LocalDataSource、Repository 组合、UI、ViewModel、RiskEngine、评分和 AI。
  - 验证结果：Debug、Release 单元测试各 53 项通过；当前腾讯固定为 DELAYED，fallback 固定为 MOCK；Debug APK 构建通过。
  - 交接结论：远程层与 Repository 组合已完成，可以进入 UseCase Developer 阶段。
- [x] 阶段 3：新增 StockQuote 与 MarketDataRepository 富行情端口。
  - 状态：完成。
  - 负责角色：GD Domain Developer Agent。
  - 契约：`market-domain-v1`。
  - 文件范围：`domain/model/market/**`、`domain/repository/MarketDataRepository.kt`、对应 Domain 测试、架构与任务记录。
  - QA 对照：只读并行 QA 工作树中的领域契约模板，不修改 QA Agent 文件。
  - 冻结范围：Data、Room、Remote API、UI、ViewModel、RiskEngine、旧 MarketRepository 和旧 MarketQuote。
  - 验证结果：Debug、Release 单元测试共 60 项通过，Debug APK 构建通过。
  - 下一阶段：Repository 组合完成后进入 UseCase，不修改已冻结 Domain 契约。
- [x] 阶段 3.5：实现行情 Repository 组合层与旧接口兼容。
  - 状态：Developer 实现、自测与基础层集成完成，待 Architect 最终接口边界确认和 QA 真机/供应商发布门禁。
  - 负责角色：GD Repository Developer Agent。
  - 分支：`codex/repository-market-v1-2`。
  - 完成文件范围：`data/repository/**`、`data/cache/**`、必要的 `data/remote/market/RemoteMarketDataSource*`、`RoomTradeRepository.kt`、对应 Repository 测试及任务文档。
  - 完成内容：组合 Remote、进程内缓存与明确 Mock fallback；实现冻结的 `MarketDataRepository`；建立 `StockQuote -> MarketQuote` 单向兼容；新旧接口共享成功及失败刷新结果。
  - 验证结果：Debug、Release 单元测试各 53 项通过，其中新增 Repository 组合测试各 15 项；Debug APK 构建通过。
  - 冻结范围：未修改 `domain/model/market/**`、`domain/repository/MarketDataRepository.kt`、Room Entity/DAO/Database/Schema/Migration、DashboardViewModel、Compose UI、RiskEngine、评分和 AI。
  - 下一阶段：基础层不存在阻塞 UseCase 开发的代码问题，建议进入 GD UseCase Developer 阶段；发布前仍需 Architect 与 QA 按项目门禁确认。
- [x] 阶段 4：实现持仓、观察池、详情和概览 UseCase。
  - 状态：Developer 实现、真实契约激活与完整自动化门禁均已完成，待合入 `integration/v1.2-market`。
  - 负责角色：GD UseCase Developer Agent。
  - 基线与分支：`integration/v1.2-market@221f6c4` -> `codex/usecase-market-v1-2`。
  - 预期文件范围：`domain/usecase/market/**`、真实 UseCase 契约绑定与 Fake Repository 单元测试、`docs/TASK_QUEUE.md`、`docs/ARCHITECTURE.md`、`CHANGELOG.md`、`TASK_COMPLETION.md`。
  - 实现边界：只组合 `PortfolioRepository`、`MarketRepository` 与冻结的 `MarketDataRepository`；空持仓/观察池不请求行情；保留顺序、状态、错误、缺失代码、完整度、逐只来源和数据状态。
  - 冻结范围：`domain/model/market/**`、Repository 接口、`DefaultMarketDataRepository`、`DashboardViewModel`、Compose UI、RiskEngine、Room、评分、AI、推送和自动交易。
  - 完成内容：四个 UseCase、显式空/非法范围、行情研究用途分级、市场概览 `InsufficientData`、Fake Repository 测试与真实 `MarketUseCaseContractTest` 绑定。
  - 验证结果：Debug、Release 单元测试各 88 项通过，失败/错误/跳过均为 0；Debug APK 构建与 `git diff --check` 通过。
  - 下一阶段：建议进入 DashboardViewModel Integration；接入时保持 UI 布局不变，不把 UseCase 状态重新压平，并继续冻结评分、AI 和自动交易。
- [ ] 阶段 5：DashboardViewModel 接入 UseCase，UI 布局保持不变。
- [ ] 阶段 6：在行情质量契约稳定后实现股票评分。

## P1

- [ ] 推送提醒。
- [ ] 股票详情页。

## P2

- [ ] AI 评分模型。
- [ ] 每日复盘模块。

## 任务执行约束

- 新任务不得突破 V1 不自动交易的边界。
- 涉及买入研究信号时，必须保留 RiskEngine 否决能力。
- 行情数据任务必须明确真实接口、延迟数据和静态样例的区别。
- 完成任务后至少运行相关单元测试和 Debug 构建检查。
