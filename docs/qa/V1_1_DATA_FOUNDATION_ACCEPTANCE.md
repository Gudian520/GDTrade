# V1.1 数据基础验收报告

## 验收结论

**自动化验收通过，稳定版暂缓签字。**

Room 数据访问、SharedPreferences 旧数据导入、Repository 契约、Dashboard 状态组合、Java 17 构建和 Debug APK 均已通过。本轮真实设备已安装 `1.5.0/versionCode 2`，缺少旧版 APK 与旧版数据基线，因此尚未完成一次从旧版到 Room 新版的原位覆盖升级。完成该项前，不建议把 V1.1 标记为稳定发布完成。

可以并行开始 V1.2 的架构设计和独立功能开发，但不得关闭 V1.1 数据迁移发布门禁。

## 测试环境

- 日期：2026-07-14
- 操作系统：Windows
- Java：Microsoft OpenJDK 17.0.14
- Gradle：8.10.2
- Android Gradle Plugin：8.7.3
- Room：2.6.1
- 测试框架：JUnit 4、Robolectric、AndroidX Room Testing、kotlinx-coroutines-test
- 真机：OPPO PLG110，Android 16，ADB 在线
- 真机当前应用：`versionName 1.5.0`、`versionCode 2`

## 测试矩阵

| 编号 | 范围 | 场景 | 结果 | 证据或说明 |
| --- | --- | --- | --- | --- |
| DB-01 | Room DAO | Position、TradeRecord、StockCandidate 增删查与顺序 | 通过 | `daoSupportsThreeDataTypes` |
| MG-01 | 旧数据迁移 | 三类正常旧数据完整导入 | 通过 | `legacyDataIsMigratedOnFirstUpgrade` |
| MG-02 | 旧数据迁移 | 空列表保持为空 | 通过 | `emptyLegacyListsRemainEmpty` |
| MG-03 | 旧数据迁移 | 重复 TradeRecord 完整保留 | 通过 | `duplicateLegacyTradeRecordsArePreserved` |
| MG-04 | 幂等性 | 迁移完成后重复启动不覆盖 Room 数据 | 通过 | `completedMigrationDoesNotOverwriteRoomData` |
| MG-05 | 异常中断 | 事务异常回滚，随后可重试迁移 | 通过 | `interruptedTransactionRollsBackAndMigrationCanRetry` |
| MG-06 | 异常恢复 | 数据已提交但标记未写入时，重启不重复导入 | 通过 | `committedDataWithoutMarkerIsRecognizedAfterRestart` |
| MG-07 | 首次安装 | 无旧数据时创建默认数据且行情标记非实时 | 通过 | `defaultDataIsCreatedWithoutLegacyData` |
| RP-01 | Repository | PortfolioRepository 观察、增删、重置 | 通过 | `portfolioAndMarketRepositoryContractsRemainUsable` |
| RP-02 | Repository | MarketRepository 候选增删、刷新、重置 | 通过 | 同上 |
| RP-03 | 静态行情 | 筛选正确且明确标记“非实时行情” | 通过 | `staticMarketRepositoryQuotesAreClearlyNonRealtime` |
| VM-01 | Dashboard | 四类 Flow 状态组合与持仓更新 | 通过 | `dashboardCombinesRepositoryStateAndAppliesRiskDecision` |
| VM-02 | 风险否决 | 等待回调候选被 RiskEngine 否决买入 | 通过 | 同上 |
| BE-01 | 构建环境 | Java 17 | 通过 | OpenJDK 17.0.14 |
| BE-02 | Gradle | `gradle test --no-daemon` | 通过 | Debug、Release 共 13 个独立用例、26 次变体执行，无失败 |
| BE-03 | Debug APK | `gradle assembleDebug --no-daemon` | 通过 | 9,013,223 字节，SHA-256 `670F2F5F5D490178AB4B93445013E065F6768F920CB47EC1AEA84BE826F6C549` |
| DEV-01 | 真机覆盖升级 | 旧版三类数据原位升级 | 待执行 | 当前设备已是新版，未破坏现有用户数据进行降级 |
| DEV-02 | 真机异常中断 | 首次迁移时强停并恢复 | 待执行 | 见真实设备测试方案 |

## 通过项目

- 三类 Room Entity 与 DAO 的基础行为。
- 正常、空数据、重复启动、重复交易记录、事务中断和标记中断迁移场景。
- PortfolioRepository 与 MarketRepository 现有接口回归。
- DashboardViewModel 状态组合、响应式更新和风险否决。
- 静态行情“非实时”标识边界。
- Java 17 下 Debug/Release 单元测试与 Debug APK 构建。

## 失败项目

- 自动化测试无失败。
- 构建无失败。
- 真机覆盖升级不是失败，而是因缺少旧版数据基线尚未执行，属于发布阻断项。

## 风险

1. 当前迁移是 SharedPreferences 到 Room 的应用级导入，不是 Room schema 版本升级；数据库目前为 version 1。未来升级到 version 2 前必须增加显式 `Migration` 与仪器测试。
2. 旧数据解析使用 `mapNotNull`，格式损坏的单条记录会被跳过，当前没有错误计数或用户提示，存在静默丢失风险。
3. 迁移完成标记写入发生在 Room 事务之后。现有恢复逻辑可以避免重复导入，但真机进程终止场景仍需执行验证。
4. Android Studio 的 JetBrains Runtime 21 在当前电脑出现过 JVM 崩溃；命令行 Java 17 构建稳定，应继续固定 Java 17。
5. 路线图称 V1.1，但应用版本已为 1.5.0，发布与开发版本口径需要统一。

## 稳定版门禁

V1.1 进入稳定版本前必须完成：

1. 按 `V1_1_DEVICE_UPGRADE_TEST_PLAN.md` 完成至少一台真实设备的旧版原位覆盖升级。
2. 保存升级前后数据对照证据，并确认三类数据字段和重复记录均完整。
3. 完成一次真机异常中断恢复验证。
4. 对损坏旧记录的处理策略作出明确决定：阻止完成、记录告警或提供导出恢复入口。

## 是否建议进入 V1.2

- **稳定发布结论：暂不建议签字。** 真机迁移门禁未完成。
- **研发并行结论：可以有条件进入。** V1.2 可先进行架构设计、行情契约冻结和独立测试开发，不应修改当前迁移逻辑或覆盖 V1.1 验收现场。
