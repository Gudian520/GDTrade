package com.gudian.gdtrade.domain.usecase.market

import com.gudian.gdtrade.data.repository.MarketRepository
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.market.FetchPolicy
import com.gudian.gdtrade.domain.model.market.MarketDataError
import com.gudian.gdtrade.domain.model.market.MarketDataState
import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.QuoteRequest
import com.gudian.gdtrade.domain.model.market.QuoteRequestReason
import com.gudian.gdtrade.domain.model.market.QuoteSnapshot
import com.gudian.gdtrade.domain.repository.MarketDataRepository
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

class GetWatchlistQuotesUseCase(
    private val marketRepository: MarketRepository,
    private val marketDataRepository: MarketDataRepository,
    private val policy: FetchPolicy = FetchPolicy.NETWORK_FIRST,
    private val maxAge: Duration = Duration.ofMinutes(1)
) {
    init {
        require(!maxAge.isNegative) { "观察池行情 maxAge 不能为负数" }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<WatchlistQuotesResult> {
        return marketRepository.observeCandidates().transformLatest { candidates ->
            val scope = prepareScope(candidates)
            when {
                candidates.isEmpty() -> emit(emptyResult())
                scope.orderedSymbols.isEmpty() -> emit(invalidOnlyResult(scope))
                else -> emitAll(observeMarketData(scope))
            }
        }
    }

    private fun observeMarketData(scope: WatchlistScope): Flow<WatchlistQuotesResult> {
        val request = QuoteRequest(
            symbols = scope.orderedSymbols.toCollection(linkedSetOf()),
            policy = policy,
            maxAge = maxAge,
            reason = QuoteRequestReason.WATCHLIST
        )
        return marketDataRepository.observeQuotes(request).map { state ->
            val quotes = state.data?.quotes.orEmpty()
            WatchlistQuotesResult(
                collectionState = QuoteCollectionState.ACTIVE,
                items = scope.candidates.mapIndexed { index, candidate ->
                    val normalizedSymbol = scope.normalizedSymbols[index]
                    WatchlistQuoteItem(
                        candidate = candidate,
                        normalizedSymbol = normalizedSymbol,
                        quote = normalizedSymbol?.let(quotes::get)
                    )
                },
                orderedSymbols = scope.orderedSymbols,
                invalidSymbols = scope.invalidSymbols,
                marketDataState = state,
                quality = MarketDataQualityInterpreter.assess(
                    state = state,
                    hasInvalidSymbols = scope.invalidSymbols.isNotEmpty()
                )
            )
        }
    }

    private fun prepareScope(candidates: List<StockCandidate>): WatchlistScope {
        val normalizedSymbols = candidates.map { MarketSymbolNormalizer.normalize(it.symbol) }
        val orderedSymbols = normalizedSymbols.filterNotNull().distinct()
        val invalidSymbols = candidates.zip(normalizedSymbols)
            .filter { (_, normalized) -> normalized == null }
            .map { (candidate, _) -> candidate.symbol }
            .distinct()
        return WatchlistScope(
            candidates = candidates,
            normalizedSymbols = normalizedSymbols,
            orderedSymbols = orderedSymbols,
            invalidSymbols = invalidSymbols
        )
    }

    private fun emptyResult(): WatchlistQuotesResult {
        return WatchlistQuotesResult(
            collectionState = QuoteCollectionState.EMPTY,
            items = emptyList(),
            orderedSymbols = emptyList(),
            invalidSymbols = emptyList(),
            marketDataState = null,
            quality = MarketDataQualityInterpreter.emptyScope()
        )
    }

    private fun invalidOnlyResult(scope: WatchlistScope): WatchlistQuotesResult {
        val state = invalidSymbolState()
        return WatchlistQuotesResult(
            collectionState = QuoteCollectionState.INVALID_SYMBOLS_ONLY,
            items = scope.candidates.map { candidate ->
                WatchlistQuoteItem(candidate = candidate, normalizedSymbol = null, quote = null)
            },
            orderedSymbols = emptyList(),
            invalidSymbols = scope.invalidSymbols,
            marketDataState = state,
            quality = MarketDataQualityInterpreter.invalidSymbolsOnly()
        )
    }

    private fun invalidSymbolState(): MarketDataState<QuoteSnapshot> = MarketDataState(
        status = MarketDataStatus.ERROR,
        data = null,
        error = MarketDataError(
            code = "INVALID_WATCHLIST_SYMBOL",
            message = "观察池中没有可请求的六位股票代码",
            retryable = false,
            affectedSymbols = emptySet(),
            providerId = null
        )
    )

    private data class WatchlistScope(
        val candidates: List<StockCandidate>,
        val normalizedSymbols: List<String?>,
        val orderedSymbols: List<String>,
        val invalidSymbols: List<String>
    )
}
