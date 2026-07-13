package com.gudian.gdtrade.data.repository

import com.gudian.gdtrade.domain.model.MarketQuote
import com.gudian.gdtrade.domain.model.StockCandidate
import kotlinx.coroutines.flow.Flow

interface MarketRepository {
    fun observeQuotes(symbols: List<String>): Flow<List<MarketQuote>>

    fun observeCandidates(): Flow<List<StockCandidate>>

    suspend fun addCandidate(candidate: StockCandidate)

    suspend fun removeCandidate(symbol: String)

    suspend fun resetMarketData()
}
