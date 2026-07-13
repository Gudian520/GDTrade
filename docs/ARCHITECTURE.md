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

- 维护 Position、TradeRecord、StockCandidate、MarketQuote 等领域模型。
- RiskEngine 负责风险决策，不依赖 Android UI 或具体数据来源。
- Repository 接口作为 Domain/UI 与 Data 实现之间的边界。

### Data 层

- Repository 实现负责协调 Room、本地迁移和行情数据源。
- Room Entity、DAO、Database 与领域模型映射独立维护。
- 行情接口不可用时可以回退静态样例，但必须明确标注为非实时行情。

## RiskEngine 设计原则

- 风险否决优先于研究型买入信号。
- 风险引擎输出只用于研究辅助和提醒，不触发自动交易。
- 每条风险规则应可独立测试，并说明触发条件和否决原因。
- 未满足数据完整性或时效性要求时，应采取保守结论。
- 最终证券买卖行为必须由用户在外部行情或交易软件中人工确认。
