package com.gudian.gdtrade.domain.usecase.market

import com.gudian.gdtrade.domain.model.market.FetchPolicy
import com.gudian.gdtrade.domain.model.market.MarketDataError
import com.gudian.gdtrade.domain.model.market.MarketDataState
import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.QuoteRequestReason
import com.gudian.gdtrade.domain.model.market.SingleQuoteRequest
import com.gudian.gdtrade.domain.model.market.StockQuote
import com.gudian.gdtrade.domain.repository.MarketDataRepository
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class GetStockDetailUseCase(
    private val marketDataRepository: MarketDataRepository,
    private val policy: FetchPolicy = FetchPolicy.NETWORK_FIRST,
    private val maxAge: Duration = Duration.ofMinutes(1)
) {
    init {
        require(!maxAge.isNegative) { "股票详情行情 maxAge 不能为负数" }
    }

    operator fun invoke(symbol: String): Flow<StockDetailResult> {
        val normalizedSymbol = MarketSymbolNormalizer.normalize(symbol)
            ?: return flowOf(invalidSymbolResult(symbol))
        val request = SingleQuoteRequest(
            symbol = normalizedSymbol,
            policy = policy,
            maxAge = maxAge,
            reason = QuoteRequestReason.STOCK_DETAIL
        )
        return marketDataRepository.observeQuote(request).map { state ->
            StockDetailResult(
                requestedSymbol = symbol,
                normalizedSymbol = normalizedSymbol,
                marketDataState = state,
                quality = MarketDataQualityInterpreter.assess(state)
            )
        }
    }

    private fun invalidSymbolResult(symbol: String): StockDetailResult {
        val state: MarketDataState<StockQuote> = MarketDataState(
            status = MarketDataStatus.ERROR,
            data = null,
            error = MarketDataError(
                code = "INVALID_STOCK_DETAIL_SYMBOL",
                message = "股票详情代码必须是六位数字",
                retryable = false,
                affectedSymbols = emptySet(),
                providerId = null
            )
        )
        return StockDetailResult(
            requestedSymbol = symbol,
            normalizedSymbol = null,
            marketDataState = state,
            quality = MarketDataQualityAssessment(
                usage = MarketDataUsage.UNAVAILABLE,
                reasons = setOf(
                    MarketDataQualityReason.INVALID_SYMBOL,
                    MarketDataQualityReason.ERROR,
                    MarketDataQualityReason.NO_DATA
                )
            )
        )
    }
}
