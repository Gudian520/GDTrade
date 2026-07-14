package com.gudian.gdtrade.data.repository

import android.content.Context
import com.gudian.gdtrade.data.local.datasource.PortfolioLocalDataSource
import com.gudian.gdtrade.data.local.datasource.RoomLocalDataSource
import com.gudian.gdtrade.data.local.datasource.WatchlistLocalDataSource
import com.gudian.gdtrade.data.local.storageKey
import com.gudian.gdtrade.domain.model.AccountGoal
import com.gudian.gdtrade.domain.model.MarketQuote
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord
import com.gudian.gdtrade.domain.model.market.FetchPolicy
import com.gudian.gdtrade.domain.model.market.MarketDataState
import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.QuoteRequest
import com.gudian.gdtrade.domain.model.market.QuoteRequestReason
import com.gudian.gdtrade.domain.model.market.QuoteSnapshot
import com.gudian.gdtrade.domain.model.market.RefreshMarketResult
import com.gudian.gdtrade.domain.model.market.SingleQuoteRequest
import com.gudian.gdtrade.domain.model.market.StockQuote
import com.gudian.gdtrade.domain.repository.MarketDataRepository
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update

open class RoomTradeRepository internal constructor(
    private val portfolioLocalDataSource: PortfolioLocalDataSource,
    private val watchlistLocalDataSource: WatchlistLocalDataSource,
    private val marketDataRepository: MarketDataRepository = DefaultMarketDataRepository.createDefault(),
    private val legacyQuoteAdapter: StockQuoteLegacyAdapter = StockQuoteLegacyAdapter(),
    private val clock: Clock = Clock.systemUTC()
) : PortfolioRepository, MarketRepository, MarketDataRepository {
    constructor(context: Context) : this(RoomLocalDataSource.create(context))

    private constructor(localDataSource: RoomLocalDataSource) : this(
        portfolioLocalDataSource = localDataSource,
        watchlistLocalDataSource = localDataSource
    )

    private val marketRefreshRequests = MutableStateFlow(0L)

    override fun observeAccountGoals(): Flow<List<AccountGoal>> {
        return flowOf(listOf(11500, 12500, 13800, 15000).map { AccountGoal(it, false) })
    }

    override fun observePositions(): Flow<List<Position>> {
        return portfolioLocalDataSource.observePositions()
    }

    override fun observeTradeRecords(): Flow<List<TradeRecord>> {
        return portfolioLocalDataSource.observeTradeRecords()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeQuotes(symbols: List<String>): Flow<List<MarketQuote>> {
        return flow {
            var lastSeenRefresh = marketRefreshRequests.value
            val requests = combine(
                observePositions(),
                marketRefreshRequests
            ) { currentPositions, refreshVersion ->
                val requestedSymbols = if (symbols.isEmpty()) {
                    currentPositions.map { it.symbol }
                } else {
                    symbols
                }
                LegacyQuoteRequest(
                    symbols = requestedSymbols.map(String::trim)
                        .filter(SIX_DIGIT_SYMBOL::matches)
                        .distinct(),
                    positions = currentPositions,
                    refreshVersion = refreshVersion
                )
            }
            emitAll(
                requests.transformLatest { request ->
                    val forceRefresh = request.refreshVersion != lastSeenRefresh
                    lastSeenRefresh = request.refreshVersion
                    if (request.symbols.isEmpty()) {
                        emit(emptyList())
                        return@transformLatest
                    }

                    val richRequest = QuoteRequest(
                        symbols = request.symbols.toCollection(linkedSetOf()),
                        policy = if (forceRefresh) {
                            FetchPolicy.NETWORK_FIRST
                        } else {
                            FetchPolicy.CACHE_FIRST
                        },
                        maxAge = LEGACY_MAX_AGE,
                        reason = QuoteRequestReason.BATCH
                    )
                    val positionNames = request.positions.associate { it.symbol to it.name }
                    emitAll(
                        marketDataRepository.observeQuotes(richRequest)
                            .filter { it.status != MarketDataStatus.LOADING }
                            .map { state ->
                                val richQuotes = state.data?.quotes.orEmpty()
                                request.symbols.mapNotNull { symbol ->
                                    richQuotes[symbol]?.let { quote ->
                                        val legacy = legacyQuoteAdapter.toLegacy(
                                            quote = quote,
                                            now = clock.instant(),
                                            maxAge = LEGACY_MAX_AGE,
                                            requestStatus = state.status
                                        )
                                        if (legacy.name == symbol) {
                                            legacy.copy(name = positionNames[symbol] ?: legacy.name)
                                        } else {
                                            legacy
                                        }
                                    }
                                }
                            }
                    )
                }
            )
        }.flowOn(Dispatchers.IO)
    }

    override fun observeQuote(request: SingleQuoteRequest): Flow<MarketDataState<StockQuote>> {
        return marketDataRepository.observeQuote(request)
    }

    override fun observeQuotes(request: QuoteRequest): Flow<MarketDataState<QuoteSnapshot>> {
        return marketDataRepository.observeQuotes(request)
    }

    override suspend fun refreshQuotes(request: QuoteRequest): RefreshMarketResult {
        val result = marketDataRepository.refreshQuotes(request)
        marketRefreshRequests.update { it + 1L }
        return result
    }

    override fun observeCandidates(): Flow<List<StockCandidate>> {
        return watchlistLocalDataSource.observeCandidates()
    }

    override suspend fun addPosition(position: Position) {
        val normalized = position.copy(
            symbol = position.symbol.trim(),
            name = position.name.trim()
        )
        if (normalized.symbol.isBlank() || normalized.name.isBlank() || normalized.quantity <= 0) return
        portfolioLocalDataSource.upsertPosition(normalized)
    }

    override suspend fun removePosition(symbol: String) {
        portfolioLocalDataSource.deletePosition(symbol)
    }

    override suspend fun addTradeRecord(record: TradeRecord) {
        if (
            record.symbol.isBlank() ||
            record.name.isBlank() ||
            record.quantity <= 0 ||
            record.price <= 0.0
        ) {
            return
        }
        portfolioLocalDataSource.insertTradeRecord(record)
    }

    override suspend fun removeTradeRecord(recordKey: String) {
        portfolioLocalDataSource.deleteTradeRecord(recordKey)
    }

    override suspend fun resetPortfolioData() {
        portfolioLocalDataSource.resetPortfolioData()
    }

    override suspend fun addCandidate(candidate: StockCandidate) {
        val normalized = candidate.copy(
            symbol = candidate.symbol.trim(),
            name = candidate.name.trim()
        )
        if (normalized.symbol.isBlank() || normalized.name.isBlank()) return
        watchlistLocalDataSource.upsertCandidate(normalized)
    }

    override suspend fun removeCandidate(symbol: String) {
        watchlistLocalDataSource.deleteCandidate(symbol)
    }

    override suspend fun refreshMarketQuotes() {
        marketRefreshRequests.update { it + 1L }
    }

    override suspend fun resetMarketData() {
        watchlistLocalDataSource.resetWatchlist()
    }

    private data class LegacyQuoteRequest(
        val symbols: List<String>,
        val positions: List<Position>,
        val refreshVersion: Long
    )

    private companion object {
        val LEGACY_MAX_AGE: Duration = Duration.ofSeconds(30)
        val SIX_DIGIT_SYMBOL = Regex("^[0-9]{6}$")
    }
}

internal val TradeRecord.localKey: String
    get() = storageKey
