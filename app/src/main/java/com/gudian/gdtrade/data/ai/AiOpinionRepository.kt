package com.gudian.gdtrade.data.ai

import com.gudian.gdtrade.domain.model.MarketQuote
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord

interface AiOpinionRepository {
    suspend fun requestOpinion(
        positions: List<Position>,
        quotes: List<MarketQuote>,
        candidates: List<StockCandidate>,
        tradeRecords: List<TradeRecord>
    ): AiOpinionResult
}

data class AiOpinionResult(
    val content: String,
    val fromRemote: Boolean
)