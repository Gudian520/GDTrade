package com.gudian.gdtrade.ui.dashboard

import com.gudian.gdtrade.domain.model.MarketQuote
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord

data class DashboardUiState(
    val positions: List<Position> = emptyList(),
    val quotes: List<MarketQuote> = emptyList(),
    val candidates: List<StockCandidate> = emptyList(),
    val tradeRecords: List<TradeRecord> = emptyList(),
    val chatGptPrompt: String = "点击复制按钮后生成适合粘贴到 ChatGPT Plus 的分析提示词。",
    val disclosure: String = "V1使用参考行情与本地数据进行研究辅助，不代表实时行情，不自动提交证券买卖订单。"
)
