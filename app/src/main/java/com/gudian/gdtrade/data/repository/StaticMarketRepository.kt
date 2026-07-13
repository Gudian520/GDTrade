package com.gudian.gdtrade.data.repository

import com.gudian.gdtrade.domain.model.MarketQuote
import com.gudian.gdtrade.domain.model.SignalStatus
import com.gudian.gdtrade.domain.model.StockCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class StaticMarketRepository : MarketRepository {
    private val quotes = listOf(
        MarketQuote("002185", "华天科技", 24.62, null, "V1静态样例，非实时行情", false),
        MarketQuote("515070", "华夏中证人工智能主题ETF", null, null, "V1静态样例，非实时行情", false),
        MarketQuote("000725", "京东方A", 6.92, null, "V1静态样例，非实时行情", false)
    )

    private val candidates = listOf(
        StockCandidate(
            symbol = "002185",
            name = "华天科技",
            theme = "半导体封测",
            reason = "持仓仍在观察区，卖出后优先跟踪风险释放情况。",
            signalStatus = SignalStatus.HoldWatch,
            riskDeniedBuy = false
        ),
        StockCandidate(
            symbol = "515070",
            name = "华夏中证人工智能主题ETF",
            theme = "人工智能主题",
            reason = "主题弹性较高，V1仅提示观察，不形成自动交易。",
            signalStatus = SignalStatus.WaitPullback,
            riskDeniedBuy = true
        ),
        StockCandidate(
            symbol = "000725",
            name = "京东方A",
            theme = "面板与消费电子",
            reason = "已有减仓记录，继续等待结构确认。",
            signalStatus = SignalStatus.ReduceRisk,
            riskDeniedBuy = true
        ),
        StockCandidate(
            symbol = "300750",
            name = "宁德时代",
            theme = "新能源权重观察",
            reason = "仅作为动态观察池样例，用于验证候选股票扩展能力。",
            signalStatus = SignalStatus.WatchOnly,
            riskDeniedBuy = false
        )
    )

    override fun observeQuotes(symbols: List<String>): Flow<List<MarketQuote>> {
        return flowOf(quotes.filter { it.symbol in symbols })
    }

    override fun observeCandidates(): Flow<List<StockCandidate>> = flowOf(candidates)
}
