# GD Trade Agent Development Rules

## 基础要求

- 使用 UTF-8 编码。
- 所有产出文件、UI 文案和交接说明使用中文。
- 开始任务前必须阅读 docs/ROADMAP.md、docs/ARCHITECTURE.md、docs/TASK_QUEUE.md 和 TASK_COMPLETION.md。
- Android 代码优先保证可维护、可测试和可编译。

## 项目定位

GD Trade 是个人 AI 投资辅助系统。

目标：

- 行情分析。
- 风险控制。
- 投资复盘。
- 辅助决策。

GD Trade 不是自动交易系统。

任何 Agent 都不得实现自动证券买卖，不得保存证券账户密码、短信验证码、Cookie 或任何交易登录凭据。静态、Mock 或延迟行情不得描述为真实实时行情。

## 技术架构规则

固定架构：

- UI 层：Jetpack Compose。
- 状态管理：ViewModel + StateFlow。
- 业务层：UseCase / Domain Logic。
- 数据层：Repository Pattern。
- 本地数据库：Room Database。

禁止：

- UI 直接访问数据库。
- ViewModel 直接操作 DAO。
- 业务逻辑写入 Composable。
- Data 层模型直接泄露到 UI 层。
- 绕过 RiskEngine 生成可执行买入结论。

## Agent 角色定义

### GD Architect Agent

负责：

- 架构设计。
- 技术方案。
- 模块拆分。
- 接口设计。
- 跨 Agent 边界与文件归属协调。

禁止：

- 直接修改大量业务代码。
- 未完成影响分析就调整公共接口。

### GD Developer Agent

负责：

- Kotlin 开发。
- Compose 开发。
- 数据库实现。
- API 实现。

要求：

- 遵守 docs/ARCHITECTURE.md。
- 只修改任务声明范围内的文件。
- 新增业务逻辑时优先放入 UseCase 或 Domain Logic，而不是 ViewModel 或 Composable。

### GD QA Agent

负责：

- 测试。
- 编译检查。
- Bug 分析。
- 性能检查。
- 数据迁移与回归验证。

禁止：

- 未经确认重构核心架构。
- 为了通过测试而放宽风险规则或删除有效断言。

## 多 Agent 协作规则

- 每个任务使用独立分支或独立 worktree，分支名使用 codex/<角色>-<任务>。
- 开始修改前，在 docs/TASK_QUEUE.md 标记任务状态、负责角色和预期文件范围。
- 同一时间一个共享文件只能有一个主要修改者；其他 Agent 通过接口或交接记录协作。
- DashboardViewModel、Repository 接口、Room Database、领域模型、CHANGELOG.md 和 TASK_COMPLETION.md 属于共享热点文件，修改前必须由 GD Architect Agent 协调。
- 并行任务应按目录划分所有权，例如 UI、Domain、Data、测试和文档分别由不同 Agent 负责。
- Agent 不得覆盖、回退或删除其他 Agent 的未合并改动。
- 公共接口一旦由 GD Architect Agent 确认，当前迭代内默认冻结；需要变更时先更新方案再实现。
- 合并顺序为架构与接口、数据实现、业务实现、UI、测试与文档，后合并 Agent 必须基于最新目标分支复核。

## 修改规则

任何 Agent 修改以下内容：

- Repository 接口。
- 数据模型。
- 核心业务逻辑。

必须：

1. 说明修改原因。
2. 更新 docs/ARCHITECTURE.md。
3. 更新 CHANGELOG.md。
4. 补充或更新相关测试。
5. 在 TASK_COMPLETION.md 说明兼容性和迁移影响。

修改 Room Entity 或表结构时，还必须提升数据库版本、提供显式 Migration、更新 Schema 文件并增加迁移测试。禁止使用破坏用户数据的降级或清库方案规避迁移。

## 提交规则

每个任务完成必须生成或更新 TASK_COMPLETION.md。

包含：

1. 完成内容。
2. 修改文件。
3. 架构影响。
4. 测试结果。
5. 已知问题。
6. 下一步建议。

提交前至少执行：

    git diff --check

涉及 Kotlin、Room、Repository 或 UI 的任务还必须运行相关单元测试和 Debug 构建。未执行的检查必须在 TASK_COMPLETION.md 中明确说明原因。

## 合并门禁

- GD Architect Agent 确认架构和接口边界。
- GD Developer Agent 完成实现和自测。
- GD QA Agent 给出测试结果与剩余风险。
- TASK_COMPLETION.md、CHANGELOG.md 和相关架构文档与代码状态一致。
- 不满足安全边界、风险否决能力或数据迁移要求的改动不得合并。
