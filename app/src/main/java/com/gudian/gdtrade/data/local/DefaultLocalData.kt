package com.gudian.gdtrade.data.local

import com.gudian.gdtrade.domain.model.MarketQuote
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.SignalStatus
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord
import com.gudian.gdtrade.domain.model.TradeSide
import java.time.LocalDate

object DefaultLocalData {
    val positions = listOf(
        Position("002185", "华天科技", 200, "当前持仓，V1只做监控与提醒。"),
        Position("515070", "华夏中证人工智能主题ETF", 800, "当前持仓，跟踪主题风险。"),
        Position("000725", "京东方A", 100, "当前持仓，减仓后观察。")
    )

    val tradeRecords = listOf(
        TradeRecord(
            LocalDate.of(2026, 7, 13),
            "002185",
            "华天科技",
            TradeSide.Sell,
            24.62,
            100,
            "已知交易记录"
        ),
        TradeRecord(
            LocalDate.of(2026, 7, 13),
            "000725",
            "京东方A",
            TradeSide.Sell,
            6.92,
            200,
            "已知交易记录"
        )
    )

    val candidates = listOf(
        StockCandidate(
            "002185",
            "华天科技",
            "半导体封测",
            "持仓仍在观察区，卖出后优先跟踪风险释放情况。",
            SignalStatus.HoldWatch,
            false
        ),
        StockCandidate(
            "515070",
            "华夏中证人工智能主题ETF",
            "人工智能主题",
            "主题弹性较高，V1仅提示观察，不形成自动交易。",
            SignalStatus.WaitPullback,
            true
        ),
        StockCandidate(
            "000725",
            "京东方A",
            "面板与消费电子",
            "已有减仓记录，继续等待结构确认。",
            SignalStatus.ReduceRisk,
            true
        ),
        StockCandidate(
            "300750",
            "宁德时代",
            "新能源权重观察",
            "仅作为动态观察池样例，用于验证候选股票扩展能力。",
            SignalStatus.WatchOnly,
            false
        )
    )

    val fallbackQuotes = listOf(
        MarketQuote("002185", "华天科技", 24.62, null, "V1静态样例，非实时行情", false),
        MarketQuote("515070", "华夏中证人工智能主题ETF", null, null, "V1静态样例，非实时行情", false),
        MarketQuote("000725", "京东方A", 6.92, null, "V1静态样例，非实时行情", false)
    ).associateBy { it.symbol }
}
