# GD Trade V1.2 行情系统测试计划

## 文档状态

- 版本：V1.2 QA 基线 1.0。
- 日期：2026-07-14。
- 负责角色：GD QA Developer Agent。
- 实施边界：仅新增单元测试、fixture 和 QA 文档，不修改生产代码、Repository、Room、UI、ViewModel 或 RiskEngine。

## 测试范围

本计划覆盖 V1.2 行情数据从领域契约到业务编排的自动化测试基础：

- `StockQuote` 字段完整性、可空值、供应商更新时间和结构化来源。
- `MarketDataStatus` 的 `SUCCESS`、`LOADING`、`ERROR`、`DELAYED`、`MOCK` 状态边界。
- Mock、延迟、错误和不完整数据不得伪装成实时行情。
- Remote 响应的正常、空响应、字段缺失和错误格式解析契约。
- Repository 远程、fallback 和部分成功的组合行为。
- UseCase 的空请求、状态传递、风险否决与交易信号边界。

不在本次范围：真实网络请求、供应商授权验证、Room 持久化、UI 截图、真机弱网和任何自动交易能力。

## 测试分层

| 层级 | 对象 | 当前形态 | 主要断言 |
| --- | --- | --- | --- |
| Fixture 自检 | 标准行情和原始响应 | 可立即执行 | 数据可读、类型正确、安全不变量成立 |
| Domain 契约 | StockQuote、MarketDataStatus | 抽象契约模板 | 空值、时间、来源、实时标记和信号边界 |
| Parser 契约 | MarketResponseParser | 抽象契约模板 | 四类固定输入产生可分类结果 |
| Repository 组合 | Remote、Fallback、快照 | 抽象契约模板 | 调用次数、回退范围、每只股票状态 |
| UseCase | 持仓/观察池/风险组合 | 抽象契约模板 | 显式 symbol、状态透传、RiskEngine 否决优先 |
| 集成与回归 | ViewModel、真机网络 | 未来阶段 | 取消、弱网、重复刷新、可见标识 |

## 集成分支接入状态

- `MarketFixtureTest` 与 `RemoteMarketFixtureTest` 已作为可执行 fixture 自检接入。
- `StockQuoteContractTest` 已绑定 `market-domain-v1` 生产模型。
- `RemoteMarketParserContractTest` 已通过供应商专用 UTF-8 fixture 绑定腾讯 `QuoteParser` 与 `QuoteMapper`。
- `MarketRepositoryContractTest` 已绑定 `DefaultMarketDataRepository` 的 Remote、缓存与 fallback 组合。
- `MarketDataStatusContractTest` 的枚举、实时前置条件和错误边界由生产 Domain 与旧适配器测试等价覆盖；涉及交易信号的部分不在测试适配器中伪造生产策略。
- `MarketUseCaseContractTest` 继续保持抽象；V1.2 UseCase 尚未实现，本次集成禁止新增 UseCase。
- 完整 Debug 与 Release 单元测试各 73 项通过，失败、错误和跳过均为 0。

## 测试数据策略

- 时间全部使用固定 `Instant`，不依赖本机当前时间和时区。
- 主样本统一使用 `002185` / `华天科技`，便于跨层对照。
- 价格和比例保留小数；成交量使用股或份，成交额使用人民币元。
- 不可解析或未知值使用 `null`，不得使用 `0`、空字符串或本机时间伪造。
- 错误测试必须保留 symbol 和来源上下文，便于诊断批量部分失败。
- 所有原始响应 fixture 使用 UTF-8 编码，不包含真实账户、Cookie 或交易凭据。

## Mock 数据规则

1. 静态样例必须使用 `dataStatus=MOCK`。
2. 标准静态来源使用 `providerId=STATIC_SAMPLE`、`sourceType=STATIC_SAMPLE`。
3. Mock、静态样例和 fallback 的 `supportsRealtime` 必须为 `false`。
4. Mock 不得生成有效交易信号，不得被映射为 `SUCCESS` 或实时。
5. 远程与 Mock 混合时，每条数据保留自身状态，批次不得伪造为全部成功。
6. fixture 中的价格只是测试输入，不得在 UI 或文档中宣称为真实当前行情。

## 实时/延迟行情验证规则

- `SUCCESS` 只表示请求成功且字段校验通过，不单独等价于实时。
- 只有 `dataStatus=SUCCESS` 且 `source.supportsRealtime=true` 时才可生成实时标记。
- `LOADING`、`ERROR`、`DELAYED`、`MOCK` 在任何来源配置下都不得标记实时。
- 供应商声明延迟或更新时间超过新鲜度阈值时必须映射为 `DELAYED`。
- 供应商时间缺失时保留 `updatedAt=null`，不得使用 `receivedAt` 或手机时间代替。
- 当前未完成授权和时效能力验证的远程源，测试预期必须为 `DELAYED` 或更保守状态。

## Repository 测试策略

- 使用 Fake RemoteDataSource、Fake FallbackDataSource 和内存快照，不发起真实网络。
- 测试远程成功时 fallback 调用次数为零。
- 测试远程全部失败时返回结构化 `ERROR` 或明确 `MOCK`，不得返回伪 `SUCCESS`。
- 测试批量部分缺失时只向 fallback 请求缺失 symbol，并保留已成功远程数据。
- 验证 `missingSymbols`、完整度、每条状态和来源，不仅断言列表长度。
- 后续补充请求去重、取消、最新请求生效、缓存策略和新旧接口共享结果测试。
- 通过继承 `MarketRepositoryContractTest` 并实现驱动器接入未来 Repository，不复制契约断言。

## UseCase 测试策略

- 使用 Fake Repository 验证纯业务组合，不依赖 Android Context、Room 或 HTTP。
- 空持仓和空观察池不发起无意义的网络请求。
- 持仓变化后的 symbol 集合应显式、去重并可预测。
- `LOADING`、`ERROR`、`DELAYED`、`MOCK` 和缺失信息不得在 UseCase 输出中丢失。
- `ERROR` 输入不得生成有效交易信号。
- 评分或 AI 的研究型信号不得覆盖 RiskEngine 否决。
- 通过继承 `MarketUseCaseContractTest` 并接入实际 UseCase 执行同一组安全契约。

## 契约模板接入方式

1. Developer 完成生产类后，在对应测试包新增一个具体测试类。
2. 具体类继承本次提供的抽象契约测试。
3. 用薄适配器把生产类转换为契约视图，适配器不得重新实现被测规则。
4. 接入后删除与具体实现重复的断言，保留 fixture 自检作为测试数据回归门禁。
5. 每个生产阶段至少执行 `gradlew.bat test --no-daemon`、`gradlew.bat assembleDebug --no-daemon` 和 `git diff --check`。
