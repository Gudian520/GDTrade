package com.gudian.gdtrade.data.repository

import com.gudian.gdtrade.domain.model.AccountGoal
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.TradeRecord
import com.gudian.gdtrade.domain.model.TradeSide
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class StaticPortfolioRepository : PortfolioRepository {
    override fun observeAccountGoals(): Flow<List<AccountGoal>> = flowOf(
        listOf(11500, 12500, 13800, 15000).map { AccountGoal(amount = it, reached = false) }
    )

    override fun observePositions(): Flow<List<Position>> = flowOf(
        listOf(
            Position("002185", "华天科技", 200, "当前持仓，V1只做监控与提醒。"),
            Position("515070", "华夏中证人工智能主题ETF", 800, "当前持仓，跟踪主题风险。"),
            Position("000725", "京东方A", 100, "当前持仓，减仓后观察。")
        )
    )

    override fun observeTradeRecords(): Flow<List<TradeRecord>> = flowOf(
        listOf(
            TradeRecord(
                tradeDate = LocalDate.of(2026, 7, 13),
                symbol = "002185",
                name = "华天科技",
                side = TradeSide.Sell,
                price = 24.62,
                quantity = 100,
                note = "已知交易记录"
            ),
            TradeRecord(
                tradeDate = LocalDate.of(2026, 7, 13),
                symbol = "000725",
                name = "京东方A",
                side = TradeSide.Sell,
                price = 6.92,
                quantity = 200,
                note = "已知交易记录"
            )
        )
    )

    override suspend fun addPosition(position: Position) = Unit

    override suspend fun removePosition(symbol: String) = Unit

    override suspend fun addTradeRecord(record: TradeRecord) = Unit

    override suspend fun removeTradeRecord(recordKey: String) = Unit

    override suspend fun resetPortfolioData() = Unit
}
