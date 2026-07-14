# GD Trade 当前架构评审

## 评审基线

- 评审日期：2026-07-14。
- 当前工程：单一 Android app 模块。
- 当前版本配置：versionName = 1.5.0，versionCode = 2。
- 评审范围：模块划分、未来拆分能力、Repository 稳定性和 V1.1 Room Database Migration 状态。

## 当前模块划分

    app
    ├── ui
    │   ├── dashboard        Compose 页面、UI 状态、ViewModel
    │   └── theme            Compose 主题
    ├── domain
    │   ├── model            持仓、行情、观察池、交易记录等领域模型
    │   └── risk             RiskEngine 与 RiskDecision
    └── data
        ├── repository       Repository 接口、Room 实现与静态实现
        ├── local            Room Entity、DAO、Database、映射和旧数据迁移
        └── ai               可选 AI 后端代理接口

当前只有一个 Gradle 模块，但包结构已具备 UI、Domain、Data 的基本职责边界。

## 当前优点

- Compose UI 没有直接访问 Room 或 DAO。
- DashboardViewModel 依赖 PortfolioRepository 和 MarketRepository 接口，而不是 DAO。
- Room Entity、DAO、Database 和领域映射已独立放在 data/local。
- SharedPreferences 到 Room 的旧数据迁移有事务保护、完成标记和测试覆盖。
- Room Schema 已导出，可为后续数据库版本迁移提供基线。
- RiskEngine 位于 Domain 层，并保持风险否决优先于研究型买点。
- 静态行情明确标注为非实时行情，没有伪装实时能力。
- CI 已配置 Java 17、单元测试和 Debug 构建。

## 当前风险

### 1. 单模块共享热点

项目规模目前适合单模块，但 DashboardViewModel、RoomTradeRepository、Repository 接口和根级交接文档会成为多 Agent 冲突热点。并行开发必须先声明文件所有权，不能让多个 Agent 同时修改这些文件。

### 2. Repository 实现职责偏多

RoomTradeRepository 同时负责 Room 持久化、旧数据迁移触发、腾讯行情网络请求、响应解析和静态行情回退。接口层目前稳定，但实现类会随着 V1.2 行情升级继续膨胀，应在不改变现有接口的前提下逐步拆出本地数据源和远程行情数据源。

### 3. 依赖装配与 UI 层耦合

DashboardViewModel.Factory 直接创建名为 LocalPreferenceRepository 的兼容实现。ViewModel 没有直接操作 DAO，但 UI 模块仍知道具体 Repository 类名。短期可接受，后续页面增多时应建立轻量应用容器统一装配，不需要立即引入复杂依赖注入框架。

### 4. UseCase 层尚未形成

当前输入转换、候选风险处理和 ChatGPT 提示词构建主要集中在 DashboardViewModel。继续增加评分、复盘和提醒后，ViewModel 会变成业务逻辑热点。新业务应优先进入 UseCase 或 Domain Logic。

### 5. 行情接口契约较弱

observeQuotes(emptyList()) 使用空列表表示跟随当前持仓，语义隐含；行情结果也缺少统一的加载、失败、延迟和最后更新时间状态。当前接口可继续使用，但 V1.2 前应由 GD Architect Agent 设计明确的数据状态契约。

### 6. 自动化测试范围有限

已有 RiskEngine 和 Room 持久化测试，但缺少 Repository 网络回退、ViewModel 状态组合、真实设备升级迁移和 Compose UI 回归测试。多 Agent 合并时仍可能出现集成回归。

### 7. 本地构建工具链不稳定

Android Studio 当前使用的 JetBrains Runtime 21 在本机出现 JVM 访问异常。项目使用 Java 17 构建可通过，但需要统一 Gradle JDK 和稳定参数，避免不同 Agent 把环境崩溃误判为代码失败。

### 8. 版本规划与代码状态不一致

路线图把 Room Database Migration 放在 V1.1，但代码版本已为 1.5.0，且 Room 主体已经完成。任务队列后续应把 V1.1 解释为迁移验收与收尾，不能重复实现数据库层。

## Repository 稳定性

结论：现有 PortfolioRepository 和 MarketRepository 对当前持仓、观察池、交易记录和行情刷新流程相对稳定，适合作为下一阶段并行开发的冻结边界。

暂不建议立即修改接口，原因：

- UI 和静态实现均已依赖现有方法。
- Room 迁移测试已围绕当前行为建立。
- V1.1 剩余工作主要是验收，不需要扩展接口。

后续需要变更时，应先定义新的行情状态、分页或详情契约，并由 GD Architect Agent 组织一次集中接口评审。

## 是否适合未来拆分

结论：适合逐步拆分，但当前不需要立即拆成多个 Gradle 模块。

近期采用包级所有权即可支持并行：

- UI Agent：ui。
- Domain Agent：domain。
- Data Agent：data。
- QA Agent：app/src/test 和测试报告。
- Architect Agent：接口、架构文档和合并协调。

当股票详情、评分、每日复盘和提醒形成稳定边界后，再评估拆分为 core-domain、core-database、core-network 和 feature 模块。过早多模块化会增加 Gradle 配置和接口维护成本。

## V1.1 阻碍分析

Room Database Migration 主体已经完成，不存在需要重新开发的架构阻碍。进入 V1.1 验收前仍需处理：

1. 在保留旧版应用数据的手机上覆盖安装，验证持仓、观察池和交易记录完整迁移。
2. 验证空列表、重复交易记录和恢复样例在真实设备上的行为。
3. 固定 Android Studio Gradle JDK 为 Java 17，并验证稳定构建。
4. 确认迁移失败时的用户可见反馈和诊断方式，目前主要依赖测试与日志。
5. 对齐路线图、版本号和任务状态，明确 V1.1 是迁移验收还是历史里程碑。

## 下一阶段建议

1. 由 GD QA Agent 先执行 V1.1 Room 真实设备升级迁移验收，并记录测试矩阵。
2. 由 GD Architect Agent 冻结现有 Repository 接口，明确 V1.2 行情状态契约。
3. 由 GD Developer Agent 在接口确认后拆分本地数据源和远程行情数据源，避免继续扩大 RoomTradeRepository。
4. 增加 ViewModel 状态组合和行情回退测试，再开始股票评分系统。
5. 在功能边界稳定后再讨论 Gradle 多模块化，不引入复杂依赖。
