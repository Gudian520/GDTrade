# AGENTS.md

## 项目约束

- 使用 UTF-8 编码。
- 所有产出文件使用中文。
- Android 代码优先保证可维护和可编译。
- V1 不实现自动证券买卖。
- 不存储证券账户密码、短信验证码、Cookie 或任何交易登录凭据。
- 不把 mock/static 行情数据描述为真实实时行情。
- 风险引擎必须可以否决买入信号。

## 技术栈

- Kotlin
- Jetpack Compose
- Android native
- ViewModel + StateFlow
- Repository abstraction

## 开发要求

- UI 文案使用中文。
- 领域模型、仓储接口、风险规则和 UI 状态分层维护。
- 静态仓储必须明确标注“非实时行情”。
- 与交易相关的能力只能提供提醒、记录和人工确认入口。
