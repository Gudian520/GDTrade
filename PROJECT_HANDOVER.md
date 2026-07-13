# GD Trade 项目交接文档

## 1. 当前项目架构

GD Trade 是一个 Kotlin + Jetpack Compose + Android 原生项目，当前 V1 定位为 A 股持仓监控与人工决策辅助工具，不实现自动证券交易。

当前架构按职责分层：

- `domain/model`：领域模型层，定义持仓、行情、观察池、交易记录、信号状态等核心数据结构。
- `domain/risk`：风险规则层，负责根据候选股票信号和风险标记判断是否允许继续研究买入。
- `data/repository`：数据仓储层，提供持仓、交易记录、观察池、行情数据的接口与实现。
- `data/ai`：可选 GPT 后端代理接口代码，目前 App 默认不使用该模块。
- `ui/dashboard`：首页策略驾驶舱，包含 Compose UI、UI 状态和 ViewModel。
- `ui/theme`：Compose 主题配置。
- `backend`：可选 Python 后端代理示例，用于未来在服务器端安全调用 OpenAI API。

UI 状态仍由 DashboardViewModel 通过 StateFlow 对外暴露。运行时保留 LocalPreferenceRepository 作为兼容入口，实际继承 RoomTradeRepository，同时实现 PortfolioRepository 和 MarketRepository。

## 2. 已完成功能

- 首页策略驾驶舱。
- 当前持仓展示。
- 当前持仓本地新增、删除、恢复样例。
- 动态观察池展示。
- 动态观察池本地新增、删除、恢复样例。
- 交易记录展示。
- 交易记录本地新增、删除、恢复样例。
- 行情刷新按钮。
- 腾讯行情接口尝试读取持仓股票参考行情。
- 行情接口失败时回退本地静态样例或空行情提示。
- 风险引擎对观察池候选进行风险否决处理。
- 支持以下信号状态：持有观察、减仓风险提示、暂不追高、等待回调、出现研究型买点、高位不追、仅观察。
- 一键打开同花顺进行人工确认。
- 同花顺自动打开失败时给出手动打开提示，不跳转应用商店。
- 生成 ChatGPT Plus 分析提示词。
- 将 ChatGPT 分析提示词复制到系统剪贴板。
- GitHub Actions Android 检查工作流。
- Gradle Wrapper。
- 风险引擎单元测试。

## 3. 文件目录说明

```text
GDTrade/
├── AGENTS.md                                  # 项目开发约束
├── README.md                                  # 项目说明和运行方式
├── PROJECT_HANDOVER.md                        # 当前交接文档
├── build.gradle.kts                           # 根 Gradle 配置
├── settings.gradle.kts                        # Gradle 项目设置
├── gradle.properties                          # Gradle 属性
├── gradlew / gradlew.bat                      # Gradle Wrapper 启动脚本
├── .github/workflows/android-check.yml        # GitHub Actions Android 检查
├── gradle/libs.versions.toml                  # 依赖版本管理
├── app/
│   ├── build.gradle.kts                       # Android App 模块配置
│   └── src/
│       ├── debug/                             # Debug 网络安全配置
│       ├── main/
│       │   ├── AndroidManifest.xml            # App 主清单
│       │   ├── java/com/gudian/gdtrade/
│       │   │   ├── MainActivity.kt            # App 入口、同花顺跳转、剪贴板复制
│       │   │   ├── data/local/                # Room 数据库、DAO、Entity、映射和旧数据迁移
│       │   │   ├── data/ai/                   # 可选 GPT 后端代理客户端
│       │   │   ├── data/repository/           # 仓储接口、本地仓储、静态样例仓储
│       │   │   ├── domain/model/              # 领域模型
│       │   │   ├── domain/risk/               # 风险引擎
│       │   │   ├── ui/dashboard/              # 首页 UI、状态、ViewModel
│       │   │   └── ui/theme/                  # Compose 主题
│       │   └── res/                           # Android 资源
│       └── test/java/com/gudian/gdtrade/      # 单元测试
└── backend/
    └── gpt_proxy.py                           # 可选 OpenAI 后端代理示例
```

## 4. 核心类说明

- `MainActivity`：Android 入口；创建 `DashboardViewModel`；负责同花顺跳转、复制 ChatGPT 提示词到剪贴板、Toast 提示。
- `DashboardViewModel`：首页状态协调层；组合持仓、行情、观察池、交易记录 Flow；调用 `RiskEngine` 处理候选股票；提供新增、删除、恢复样例、刷新行情、生成 ChatGPT 提示词等行为。
- `DashboardUiState`：首页 UI 状态；包含持仓、行情、候选股票、交易记录、ChatGPT 提示词和 V1 边界提示文案。
- `DashboardScreen`：Compose 首页；展示当前持仓、行情与 ChatGPT 操作、本地数据维护、动态观察池、交易记录。
- `PortfolioRepository`：持仓与交易记录仓储接口。
- `MarketRepository`：行情与观察池仓储接口。
- RoomTradeRepository：当前主仓储实现；使用 Room 保存本地数据；行情接口失败时回退明确标注的非实时样例。
- LocalPreferenceRepository：为保持现有 ViewModel 装配不变而保留的兼容入口；SharedPreferences 只用于首次升级导入。
- `StaticPortfolioRepository`：静态持仓与交易记录样例仓储，主要保留为替换或测试参考。
- `StaticMarketRepository`：静态行情与观察池样例仓储，明确标注非实时行情。
- `RiskEngine`：风险引擎；当 `riskDeniedBuy` 为 true 时优先否决买入；只有 `ResearchBuyPoint` 且无风险否决时允许继续研究，但仍不自动交易。
- `RiskDecision`：风险引擎输出结果，包含是否允许、信号状态和原因。
- `SignalStatus`：信号状态枚举，包含 V1 要求的全部状态。
- `Position`：持仓模型。
- `MarketQuote`：行情模型，包含 `sourceLabel` 和 `isRealtime`，用于区分数据来源与是否实时。
- `StockCandidate`：观察池候选模型。
- `TradeRecord` / `TradeSide`：交易记录与买卖方向模型。
- `AiOpinionRepository` / `ProxyGptOpinionRepository`：可选后端 GPT 代理客户端，目前默认 UI 流程不使用。
- `backend/gpt_proxy.py`：可选 Python 后端代理示例，需要服务器环境变量 `OPENAI_API_KEY`，当前 App 默认不依赖。

## 5. 当前使用的数据来源

当前 App 通过 LocalPreferenceRepository 兼容入口使用 RoomTradeRepository：

- 持仓数据：手机本地 Room Database；旧版本 SharedPreferences 数据在首次访问时一次性迁移。
- 观察池数据：手机本地 Room Database；全新安装时使用默认样例。
- 交易记录：手机本地 Room Database；旧记录按原顺序迁移并允许重复记录。
- 行情数据：点击刷新或状态组合更新时，尝试访问腾讯行情接口 `https://qt.gtimg.cn/q=...`。
- 行情失败兜底：腾讯接口失败、解析失败或无数据时，回退 `DefaultLocalData.fallbackQuotes` 或生成“行情接口暂不可用，未使用实时行情”的空行情提示。
- ChatGPT 辅助：不直接调用 OpenAI API；由 App 生成提示词，用户复制到 ChatGPT Plus 手动分析。

注意：当前 `MarketQuote.isRealtime` 在腾讯接口解析结果中也设置为 `false`，界面文案写明“可能延迟”。项目不声称具有真实实时行情能力。

## 6. Mock 数据位置

当前样例或 Mock 数据主要在以下位置：

- `DefaultLocalData.positions`：默认持仓。
- `DefaultLocalData.tradeRecords`：默认交易记录。
- `DefaultLocalData.candidates`：默认观察池。
- `DefaultLocalData.fallbackQuotes`：默认静态行情兜底。
- `StaticPortfolioRepository`：静态持仓、账户阶段目标、交易记录样例。
- `StaticMarketRepository`：静态行情和观察池样例。
- `DashboardScreen.StaticPortfolioRepositoryPreview`：Compose Preview 使用的预览样例数据。

当前已知默认持仓：

- 华天科技 002185：200 股。
- 华夏中证人工智能主题 ETF 515070：800 份。
- 京东方 A 000725：100 股。

当前已知默认交易记录：

- 2026-07-13 华天科技 24.62 元卖出 100 股。
- 2026-07-13 京东方 A 6.92 元卖出 200 股。

## 7. 未完成模块

- 完整行情数据层：当前只尝试腾讯接口，没有多源行情、刷新频率控制、缓存过期策略和节流策略。
- 后端 GPT 代理集成：代码和后端示例已保留，但当前默认 UI 改为 ChatGPT Plus 手动粘贴方案。
- 推送提醒：V1 目标中包含推送提醒，但当前尚未实现本地通知或远程推送。
- 风险引擎规则扩展：当前规则较基础，只覆盖风险否决优先级和信号状态粗判断。
- 数据导入导出：当前没有导出本地持仓、观察池、交易记录的能力。
- UI 导航：当前只有单页首页，没有设置页、详情页、历史页。
- 错误观测：当前没有 Crashlytics、日志上报或本地错误日志页面。
- 自动化 UI 测试：当前只有风险引擎单元测试，没有 Compose UI 测试。
- 发布签名与版本管理：当前仍是基础 debug/release 配置，未配置正式签名流程。

## 8. 已知 Bug

- 同花顺深链兼容性依赖用户设备上的同花顺版本；若包名或深链格式不匹配，会提示“未能自动打开同花顺，请手动打开同花顺确认”。
- 腾讯行情接口可能受网络、地区、接口变动影响，失败时只能回退静态样例或空行情提示。
- `LocalPreferenceRepository.observeQuotes(symbols)` 当前实际与内部持仓 Flow 绑定，传入固定 symbols 时仍会随持仓变化触发，后续可拆分为更明确的查询接口。
- `observeAccountGoals()` 仍返回账户阶段目标样例，但首页已经移除账户阶段目标区域，属于暂时未使用代码。
- `data/ai` 代理客户端仍保留，但当前 UI 默认不调用，后续若恢复 API 模式需要重新接回 ViewModel 并补测试。

- 首页本地数据维护表单输入校验较弱，非法输入会静默忽略，没有明确错误提示。

## 9. 下一阶段推荐开发任务

优先级建议如下：

1. 为 Room 后续 Schema 版本变化增加显式 Migration，并实现本地数据备份与导出。
2. 为本地数据维护增加输入校验和错误提示，例如代码为空、数量非法、价格非法、日期格式错误。
3. 抽象行情数据源，增加行情状态模型：成功、失败、延迟、静态兜底、最后更新时间。
4. 扩展风险引擎规则，把“高位不追”“等待回调”“减仓风险提示”等规则拆成可测试的独立规则。
5. 增加本地提醒功能，例如观察池信号变化、风险否决、价格到达参考区间时提醒。
6. 增加详情页，分别展示单只股票的持仓、交易记录、风险历史和 ChatGPT 提示词片段。
7. 清理或正式接入 `data/ai`：如果继续采用方案 3，可把 API 客户端标为实验模块；如果走后端代理，需要补安全配置和错误处理。
8. 增加 Compose UI 测试，覆盖首页渲染、复制提示词、本地新增和删除操作。
9. 增加数据备份/导出能力，至少支持导出 JSON 或 CSV。
10. 建立正式版本签名、版本号递增和发布检查清单。

## 10. 如何继续开发

推荐流程：

1. 拉取最新代码：

```powershell
git pull origin main
```

2. 使用 Android Studio 打开项目目录：

```text
D:\Backup\Documents\GDTrade
```

3. 等待 Gradle Sync 完成，连接 Android 手机后运行 `app`。

4. 命令行检查：

```powershell
.\gradlew test
.\gradlew assembleDebug
```

5. 开发新功能时建议从独立分支开始：

```powershell
git checkout -b codex/room-local-storage
```

6. 新增功能时保持当前边界：

- 不实现自动证券买卖。
- 不保存证券账户密码、短信验证码、Cookie 或交易登录凭据。
- 不把静态样例或延迟行情描述成真实实时行情。
- 风险引擎必须可以否决买入信号。
- ChatGPT 输出只能作为研究辅助，不作为确定性交易指令。

7. 提交前至少运行：

```powershell
.\gradlew test
.\gradlew assembleDebug
```

8. 修改 Room Entity 或表结构时必须提升数据库版本、提供显式 Migration，并更新 Schema 与迁移测试。
