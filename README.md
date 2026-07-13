# GD Trade

GD Trade 是一个面向 A 股持仓监控与决策辅助的 Android 原生 App。当前 V1 只做持仓管理、观察池、风险提示、交易记录和人工确认入口，不实现自动证券交易。

## V1 边界

- 不自动提交真实证券买卖订单。
- 不存储证券账户密码、短信验证码、Cookie 或任何交易登录凭据。
- 行情层使用 `StaticMarketRepository` 静态样例数据，不声称具备真实实时行情能力。
- 风险引擎可以否决买入信号，UI 只展示提示和人工确认入口。
- “打开同花顺”只用于跳转人工确认，不代表下单。

## 当前功能

- 首页策略驾驶舱。
- 账户阶段目标：11500、12500、13800、15000。
- 当前持仓：
  - 华天科技 002185：200 股
  - 华夏中证人工智能主题 ETF 515070：800 份
  - 京东方 A 000725：100 股
- 动态观察池与信号状态展示。
- 交易记录：
  - 2026-07-13 华天科技 24.62 元卖出 100 股
  - 2026-07-13 京东方 A 6.92 元卖出 200 股
- 一键打开同花顺进行人工确认。

## 架构

项目采用分层结构：

- `domain/model`：持仓、候选股票、行情、交易记录、账户目标等领域模型。
- `domain/risk`：风险引擎与风险决策。
- `data/repository`：仓储接口与 V1 静态实现。
- `ui/dashboard`：策略驾驶舱界面、UI 状态与 ViewModel。
- `ui/theme`：Compose 主题。

UI 状态由 `DashboardViewModel` 使用 `StateFlow<DashboardUiState>` 管理。仓储层通过接口抽象，后续可替换为真实行情或本地数据库实现。

## 信号状态

V1 支持以下信号状态：

- 持有观察
- 减仓风险提示
- 暂不追高
- 等待回调
- 出现研究型买点
- 高位不追
- 仅观察

## 本地运行

推荐使用 Android Studio 打开项目并同步 Gradle。

当前仓库没有提交 Gradle Wrapper。本地如需命令行构建，应先在可信环境中生成 Gradle Wrapper，再运行：

```powershell
.\gradlew test
.\gradlew assembleDebug
```

远端 PR 已配置 GitHub Actions，会使用 Java 17 和 Gradle 8.10.2 运行：

```powershell
gradle test --no-daemon
gradle assembleDebug --no-daemon
```

## 下一步建议

1. 加入 Gradle Wrapper，固定命令行构建入口。
2. 增加本地持久化仓储，例如 Room。
3. 接入真实行情前先定义数据来源、刷新频率、延迟标识和失败兜底。
4. 扩展风险引擎规则，并为每条规则补充单元测试。
