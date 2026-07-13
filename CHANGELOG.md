# 更新日志

本项目遵循语义化版本记录。所有行情与分析能力仅用于研究辅助，不构成自动交易指令。

## [1.5.0] - 2026-07-14

### 新增

- 新增 Room Database 本地持久化层。
- 新增 PositionEntity、TradeRecordEntity、StockCandidateEntity。
- 新增持仓、交易记录、观察池 DAO。
- 新增数据库 Schema 导出配置，便于后续版本编写 Room Schema Migration。
- 新增 SharedPreferences 到 Room 的一次性迁移器。
- 新增 Room DAO、旧数据导入、空列表迁移、幂等迁移和默认数据初始化测试。

### 变更

- 持仓、交易记录和动态观察池改由 Room 保存并通过 Flow 观察。
- PortfolioRepository 和 MarketRepository 接口保持不变。
- DashboardViewModel 和现有 Compose UI 逻辑保持不变。
- 原 LocalPreferenceRepository 类名保留为兼容入口，实际读写委托给 Room 仓储。
- App 版本号更新为 1.5.0，versionCode 更新为 2。

### 数据迁移

- 从旧版本升级时，首次访问仓储会读取 gd_trade_local_data 中的持仓、交易记录和观察池数据，并在同一数据库事务中写入 Room。
- 迁移成功后写入完成标记，后续启动不会重复覆盖 Room 数据。
- 旧 SharedPreferences 数据不会自动删除，保留用于升级回退和问题排查。
- 旧版本中用户主动清空的数据会按空列表迁移，不会重新填充默认样例。
- 全新安装且没有旧数据时，会初始化明确标注的 V1 静态样例；静态行情仍标注为非实时行情。

### 安全边界

- 不实现自动证券买卖。
- 不保存证券账户密码、短信验证码、Cookie 或交易登录凭据。
- Room 仅保存持仓、观察池和用户录入的交易记录。
- 风险引擎仍可优先否决研究型买入信号。
