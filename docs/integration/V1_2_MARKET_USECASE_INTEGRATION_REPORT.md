# GD Trade V1.2 行情 UseCase 集成报告

## 1. 集成结论

- 结论：V1.2 行情 UseCase 已成功集成到 `integration/v1.2-market`，未发现阻塞下一阶段的代码问题。
- 集成角色：GD Integration Agent。
- 集成日期：2026-07-14。
- 集成方式：非快进合并并保留来源完整提交历史，不复制、不重写 UseCase 代码。
- 冲突结果：无 Git 冲突；共享文档按当前阶段状态进行了语义整理。

本次只集成已经完成的 V1.2 UseCase 与测试，不开发新功能，不接入 DashboardViewModel 或 Compose UI，也不实现股票评分、AI 日报、推送提醒或自动交易。

## 2. 分支与提交核验

- 集成前目标分支：`integration/v1.2-market`。
- 集成前目标 commit：`221f6c4dd9eb588640a352dd528f975e0f4de5a7`。
- 来源分支：`origin/codex/usecase-market-v1-2`。
- 来源最终 HEAD：`3cf1f8e9bb2e91a9cfaf8264d0fc7f49bedee0c6`。
- 核心功能 commit：`5bd3062942478d376182ec1ab466eaedfc261903`。
- 核验结果：来源远端 HEAD 与任务单完全一致；核心功能 commit 是来源最终 HEAD 的祖先；集成前目标本地与远端一致，工作区干净。
- 最终集成 commit：`5da88b65f4f08aed6fd1499ef2c2106926fc2c03`。
- 合并父提交：目标基线 `221f6c4dd9eb588640a352dd528f975e0f4de5a7`，来源 HEAD `3cf1f8e9bb2e91a9cfaf8264d0fc7f49bedee0c6`。
- 远端同步：集成提交已推送；最终交付再次确认本地与 `origin/integration/v1.2-market` 同一提交且工作区干净。

来源历史中的核心功能提交与完成记录提交均由本次合并保留，目标分支没有通过文件复制或 squash 丢失来源历史。

## 3. 集成文件

### 生产代码

- `GetPortfolioQuotesUseCase.kt`
- `GetWatchlistQuotesUseCase.kt`
- `GetStockDetailUseCase.kt`
- `GetMarketOverviewUseCase.kt`
- `MarketUseCaseModels.kt`
- `MarketSymbolNormalizer.kt`
- `MarketDataQualityInterpreter.kt`

### 测试代码

- `FakeMarketRepositories.kt`
- `MarketUseCaseFixtures.kt`
- `MarketUseCasesTest.kt`
- `RealMarketUseCaseContractTest.kt`

生产文件位于 `app/src/main/java/com/gudian/gdtrade/domain/usecase/market/`，测试文件位于对应的 `app/src/test/java/com/gudian/gdtrade/domain/usecase/market/`。

## 4. UseCase 行为复核

- 持仓与观察池均从既有 Repository Flow 获取列表，代码经首尾空白清理、常见沪深前缀清理、六位代码校验和首次出现顺序去重后，构造显式 `QuoteRequest`。
- 空持仓或空观察池返回 `QuoteCollectionState.EMPTY`，不会调用 `MarketDataRepository`。
- 仅含非法代码时返回 `INVALID_SYMBOLS_ONLY` 和结构化错误，不向 Repository 发送空集合或非法代码。
- Repository 的 `Set` 不承担 UI 顺序；UseCase 按原持仓或观察池列表恢复结果顺序，重复代码只请求一次但可映射到多个原始条目。
- 股票详情使用 `SingleQuoteRequest(reason=STOCK_DETAIL)`；非法代码直接返回领域错误且不调用 Repository。
- 市场概览在缺少全市场、板块和资金流能力时明确返回 `InsufficientData`，不以持仓或观察池伪造全市场结论。

## 5. 状态、错误和数据质量复核

- `MarketDataState`、`QuoteSnapshot`、`MarketDataError`、`missingSymbols`、`DataCompleteness`、逐只 `dataStatus` 与逐只 `source` 均完整保留。
- SUCCESS、LOADING、ERROR、DELAYED、MOCK 不会被压平为普通成功列表。
- DELAYED 只允许观察，MOCK 只允许测试，ERROR 不可用；三者都不能支持有效研究结论。
- SUCCESS 只可作为后续研究输入，不自动等价于实时，也不能绕过 RiskEngine。

## 6. QA 契约与风险边界

- `RealMarketUseCaseContractTest` 继承既有 `MarketUseCaseContractTest`，实际调用生产 `GetPortfolioQuotesUseCase`。
- 测试驱动器只准备 Fake Repository 输入和读取 UseCase 输出，不重新实现空集合、状态传播或质量分级规则。
- 风险场景组合生产 UseCase 输出与现有 `RiskEngine`，确认 RiskEngine deny 仍是最终否决。
- 本次未修改 RiskEngine，也未实现可执行买入结论或自动证券交易。

## 7. 冻结边界复核

来源分支相对集成前目标仅新增 7 个 UseCase 生产文件、4 个测试文件，并修改共享中文文档。以下生产边界均未修改：

- `domain/model/market/**`。
- `MarketDataRepository`、`PortfolioRepository`、`MarketRepository` 接口。
- `DefaultMarketDataRepository` 与其他 Data/Remote/Local 实现。
- DashboardViewModel 与 Compose UI。
- RiskEngine。
- Room Entity、DAO、Database、Schema 和 Migration。
- 股票评分、AI、推送和自动交易。

UseCase 依赖既有 Repository 接口和冻结的行情领域端口，不依赖 Data 实现、DAO 或 Remote；历史 `PortfolioRepository`、`MarketRepository` 仍位于 `data.repository` 包，属于既有架构债务，本次未顺带迁移。

## 8. 自动化门禁结果

执行：

```text
gradlew.bat test --no-daemon
gradlew.bat assembleDebug --no-daemon
git diff --check
```

结果：

- Debug 单元测试：88 项通过，失败 0，错误 0，跳过 0。
- Release 单元测试：88 项通过，失败 0，错误 0，跳过 0。
- 新增 UseCase 与真实契约测试：每个变体 15 项通过。
- Debug APK 构建：通过。
- APK：`app/build/outputs/apk/debug/app-debug.apk`。
- APK 大小：9,111,704 字节。
- APK SHA-256：`A7986F4FE83E1D7BCDF0F66BD9028342CEE8CA915EBEE2E7E3C4244B0AB97FB4`。
- `git diff --check`：通过。

## 9. 兼容性与迁移影响

- Repository 公共接口和行情领域模型保持不变。
- DashboardViewModel、现有 Compose UI 和旧行情适配路径保持不变。
- Room Schema 未变化，无需提升数据库版本、增加 Migration 或更新 Schema 文件。
- 不保存证券账户密码、短信验证码、Cookie 或任何交易登录凭据。

## 10. 已知问题与发布门禁

- 腾讯供应商授权、稳定性、字段口径和真实时效能力仍未完成生产验收，继续按 DELAYED 或更保守状态处理。
- 行情缓存仍仅在进程内，应用重启后重新请求。
- 当前缺少完整 A 股全市场、可靠板块和资金流能力，市场概览只能返回数据不足。
- DashboardViewModel 尚未接入新的 UseCase 输出，UI 暂不展示结构化状态和质量分级。
- V1.1 旧版真机原位升级、V1.2 真机供应商联调、弱网、并发和前后台专项仍是独立发布门禁。

## 11. 下一阶段建议

建议进入 DashboardViewModel Integration，但必须继续保持 UI 布局不变，直接消费 UseCase 输出，不在 ViewModel 中重建代码标准化、空集合、状态传播或质量分级规则。股票评分、AI 日报、推送和自动交易继续不在下一阶段范围内。

## 12. 最终判断

1. 是否按任务要求使用正常 Git 合并并保留来源历史：是。
2. 来源远端 HEAD、核心功能 commit 和祖先关系是否核验通过：是。
3. 7 个生产 UseCase 文件与 4 个测试文件是否完整集成：是。
4. 真实 QA 契约是否调用生产 UseCase 且保留 RiskEngine 最终否决：是。
5. 冻结接口、Dashboard/UI、RiskEngine、Room 和自动交易边界是否保持不变：是。
6. 完整单元测试、Debug 构建和差异检查是否通过：是。
7. 是否建议进入 DashboardViewModel Integration：是，但仍须遵守剩余真机、供应商和 Architect/QA 发布门禁。
