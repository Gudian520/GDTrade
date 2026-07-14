package com.gudian.gdtrade.domain.usecase.market

import com.gudian.gdtrade.data.repository.PortfolioRepository
import com.gudian.gdtrade.domain.model.Position
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

class GetPortfolioQuotesUseCase(
    private val portfolioRepository: PortfolioRepository,
    private val marketDataRepository: MarketDataRepository,
    private val policy: FetchPolicy = FetchPolicy.NETWORK_FIRST,
    private val maxAge: Duration = Duration.ofMinutes(1)
) {
    init {
        require(!maxAge.isNegative) { "持仓行情 maxAge 不能为负数" }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<PortfolioQuotesResult> {
        return portfolioRepository.observePositions().transformLatest { positions ->
            val scope = prepareScope(positions)
            when {
                positions.isEmpty() -> emit(emptyResult())
                scope.orderedSymbols.isEmpty() -> emit(invalidOnlyResult(scope))
                else -> emitAll(observeMarketData(scope))
            }
        }
    }

    private fun observeMarketData(scope: PortfolioScope): Flow<PortfolioQuotesResult> {
        val request = QuoteRequest(
            symbols = scope.orderedSymbols.toCollection(linkedSetOf()),
            policy = policy,
            maxAge = maxAge,
            reason = QuoteRequestReason.PORTFOLIO
        )
        return marketDataRepository.observeQuotes(request).map { state ->
            val quotes = state.data?.quotes.orEmpty()
            PortfolioQuotesResult(
                collectionState = QuoteCollectionState.ACTIVE,
                items = scope.positions.mapIndexed { index, position ->
                    val normalizedSymbol = scope.normalizedSymbols[index]
                    PositionQuoteItem(
                        position = position,
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

    private fun prepareScope(positions: List<Position>): PortfolioScope {
        val normalizedSymbols = positions.map { MarketSymbolNormalizer.normalize(it.symbol) }
        val orderedSymbols = normalizedSymbols.filterNotNull().distinct()
        val invalidSymbols = positions.zip(normalizedSymbols)
            .filter { (_, normalized) -> normalized == null }
            .map { (position, _) -> position.symbol }
            .distinct()
        return PortfolioScope(
            positions = positions,
            normalizedSymbols = normalizedSymbols,
            orderedSymbols = orderedSymbols,
            invalidSymbols = invalidSymbols
        )
    }

    private fun emptyResult(): PortfolioQuotesResult {
        return PortfolioQuotesResult(
            collectionState = QuoteCollectionState.EMPTY,
            items = emptyList(),
            orderedSymbols = emptyList(),
            invalidSymbols = emptyList(),
            marketDataState = null,
            quality = MarketDataQualityInterpreter.emptyScope()
        )
    }

    private fun invalidOnlyResult(scope: PortfolioScope): PortfolioQuotesResult {
        val state = invalidSymbolState()
        return PortfolioQuotesResult(
            collectionState = QuoteCollectionState.INVALID_SYMBOLS_ONLY,
            items = scope.positions.map { position ->
                PositionQuoteItem(position = position, normalizedSymbol = null, quote = null)
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
            code = "INVALID_PORTFOLIO_SYMBOL",
            message = "持仓中没有可请求的六位股票代码",
            retryable = false,
            affectedSymbols = emptySet(),
            providerId = null
        )
    )

    private data class PortfolioScope(
        val positions: List<Position>,
        val normalizedSymbols: List<String?>,
        val orderedSymbols: List<String>,
        val invalidSymbols: List<String>
    )
}
