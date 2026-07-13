package com.gudian.gdtrade.ui.dashboard

import com.gudian.gdtrade.domain.model.AccountGoal
import com.gudian.gdtrade.domain.model.MarketQuote
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord

data class DashboardUiState(
    val accountGoals: List<AccountGoal> = emptyList(),
    val positions: List<Position> = emptyList(),
    val quotes: List<MarketQuote> = emptyList(),
    val candidates: List<StockCandidate> = emptyList(),
    val tradeRecords: List<TradeRecord> = emptyList(),
    val isAiOpinionLoading: Boolean = false,
    val aiOpinion: String = "尚未生成 GPT 研究意见。",
    val disclosure: String = "V1使用静态样例数据，不代表真实实时行情，不自动提交证券买卖订单。"
)
