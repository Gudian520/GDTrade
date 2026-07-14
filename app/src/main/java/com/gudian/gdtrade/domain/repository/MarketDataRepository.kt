package com.gudian.gdtrade.domain.repository

import com.gudian.gdtrade.domain.model.market.MarketDataState
import com.gudian.gdtrade.domain.model.market.QuoteRequest
import com.gudian.gdtrade.domain.model.market.QuoteSnapshot
import com.gudian.gdtrade.domain.model.market.RefreshMarketResult
import com.gudian.gdtrade.domain.model.market.SingleQuoteRequest
import com.gudian.gdtrade.domain.model.market.StockQuote
import kotlinx.coroutines.flow.Flow

interface MarketDataRepository {
    fun observeQuote(
        request: SingleQuoteRequest
    ): Flow<MarketDataState<StockQuote>>

    fun observeQuotes(
        request: QuoteRequest
    ): Flow<MarketDataState<QuoteSnapshot>>

    suspend fun refreshQuotes(
        request: QuoteRequest
    ): RefreshMarketResult
}