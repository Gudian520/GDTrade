package com.gudian.gdtrade.data.repository

import com.gudian.gdtrade.data.cache.QuoteMemoryStore
import com.gudian.gdtrade.data.remote.market.RemoteMarketDataSource
import com.gudian.gdtrade.data.remote.market.RemoteMarketResult
import com.gudian.gdtrade.data.remote.market.TencentRemoteMarketDataSource
import com.gudian.gdtrade.data.remote.market.error.RemoteMarketError
import com.gudian.gdtrade.domain.model.market.DataCompleteness
import com.gudian.gdtrade.domain.model.market.FetchPolicy
import com.gudian.gdtrade.domain.model.market.MarketDataError
import com.gudian.gdtrade.domain.model.market.MarketDataState
import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.MarketSourceType
import com.gudian.gdtrade.domain.model.market.QuoteRequest
import com.gudian.gdtrade.domain.model.market.QuoteSnapshot
import com.gudian.gdtrade.domain.model.market.RefreshMarketResult
import com.gudian.gdtrade.domain.model.market.SingleQuoteRequest
import com.gudian.gdtrade.domain.model.market.StockQuote
import com.gudian.gdtrade.domain.repository.MarketDataRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Remote、内存缓存与明确 fallback 的唯一组合入口。 */
class DefaultMarketDataRepository(
    private val remoteDataSource: RemoteMarketDataSource,
    private val memoryStore: QuoteMemoryStore,
    private val fallbackDataSource: FallbackMarketDataSource,
    private val clock: Clock = Clock.systemUTC(),
    private val sharedRefreshWindow: Duration = Duration.ofSeconds(1)
) : MarketDataRepository {
    private val remoteRequestMutex = Mutex()
    private var latestRemoteBatch: RecentRemoteBatch? = null

    init {
        require(!sharedRefreshWindow.isNegative) { "共享刷新窗口不能为负数" }
    }

    override fun observeQuote(
        request: SingleQuoteRequest
    ): Flow<MarketDataState<StockQuote>> {
        val batchRequest = QuoteRequest(
            symbols = linkedSetOf(request.symbol),
            policy = request.policy,
            maxAge = request.maxAge,
            reason = request.reason
        )
        return observeQuotes(batchRequest).map { state ->
            MarketDataState(
                status = state.status,
                data = state.data?.quotes?.get(request.symbol),
                error = state.error
            )
        }
    }

    override fun observeQuotes(
        request: QuoteRequest
    ): Flow<MarketDataState<QuoteSnapshot>> = flow {
        val loadingQuotes = memoryStore.get(request.symbols)
        emit(
            MarketDataState(
                status = MarketDataStatus.LOADING,
                data = loadingQuotes.takeIf { it.isNotEmpty() }?.toSnapshot(
                    requestedSymbols = request.symbols,
                    generatedAt = clock.instant()
                )
            )
        )
        emit(loadQuotes(request))
    }

    override suspend fun refreshQuotes(request: QuoteRequest): RefreshMarketResult {
        val remoteResult = fetchRemoteShared(request.symbols)
        val refreshedSymbols = remoteResult.quotes.keys
        val failedSymbols = request.symbols - refreshedSymbols
        val error = buildRemoteError(
            requestedSymbols = request.symbols,
            missingSymbols = failedSymbols,
            remoteErrors = remoteResult.errors
        )
        val status = when {
            refreshedSymbols.isEmpty() -> MarketDataStatus.ERROR
            remoteResult.quotes.values.any { it.dataStatus == MarketDataStatus.MOCK } -> {
                MarketDataStatus.MOCK
            }
            remoteResult.quotes.values.any { it.dataStatus == MarketDataStatus.DELAYED } -> {
                MarketDataStatus.DELAYED
            }
            failedSymbols.isNotEmpty() -> MarketDataStatus.ERROR
            else -> MarketDataStatus.SUCCESS
        }
        val finalError = if (status == MarketDataStatus.ERROR) {
            error ?: incompleteError(failedSymbols.ifEmpty { request.symbols })
        } else {
            error
        }

        return RefreshMarketResult(
            requestedSymbols = request.symbols,
            refreshedSymbols = refreshedSymbols,
            failedSymbols = failedSymbols,
            status = status,
            error = finalError
        )
    }

    private suspend fun loadQuotes(request: QuoteRequest): MarketDataState<QuoteSnapshot> {
        val now = clock.instant()
        val previousQuotes = memoryStore.get(request.symbols)
        val cachedQuotes = if (request.policy == FetchPolicy.CACHE_FIRST) {
            memoryStore.getFresh(request.symbols, now, request.maxAge)
        } else {
            emptyMap()
        }
        val remoteTargets = request.symbols - cachedQuotes.keys
        val remoteResult = if (remoteTargets.isEmpty()) {
            null
        } else {
            fetchRemoteShared(remoteTargets)
        }
        val remoteQuotes = remoteResult?.quotes.orEmpty()

        val combined = linkedMapOf<String, StockQuote>()
        request.symbols.forEach { symbol ->
            remoteQuotes[symbol]?.let { combined[symbol] = it }
                ?: cachedQuotes[symbol]?.let { combined[symbol] = it }
        }

        val remoteMissing = remoteTargets - remoteQuotes.keys
        val fallbackTargets = if (request.policy == FetchPolicy.NETWORK_ONLY) {
            emptySet()
        } else {
            request.symbols - combined.keys
        }
        val fallbackQuotes = if (fallbackTargets.isEmpty()) {
            emptyMap()
        } else {
            fallbackDataSource.getQuotes(fallbackTargets)
                .filter { (symbol, quote) -> isValidFallback(symbol, quote, fallbackTargets) }
        }
        fallbackQuotes.forEach { (symbol, quote) ->
            if (symbol !in combined) combined[symbol] = quote
        }
        memoryStore.put(fallbackQuotes.values)

        val missingAfterFallback = request.symbols - combined.keys
        var error = buildRemoteError(
            requestedSymbols = request.symbols,
            missingSymbols = remoteMissing + missingAfterFallback,
            remoteErrors = remoteResult?.errors.orEmpty()
        )
        val status = aggregateStatus(
            requestedSymbols = request.symbols,
            quotes = combined,
            hasError = error != null || missingAfterFallback.isNotEmpty()
        )
        if (status == MarketDataStatus.ERROR && error == null) {
            error = incompleteError(missingAfterFallback.ifEmpty { request.symbols })
        }

        val data = if (combined.isNotEmpty()) {
            combined.toSnapshot(request.symbols, clock.instant())
        } else {
            previousQuotes.takeIf { it.isNotEmpty() }
                ?.toSnapshot(request.symbols, clock.instant())
        }

        return MarketDataState(
            status = status,
            data = data,
            error = error
        )
    }

    /**
     * 串行合并同一时间窗口内的重叠刷新。
     * 第二个新旧接口调用会复用刚写入的远程结果，只请求尚未覆盖的 symbol。
     */
    private suspend fun fetchRemoteShared(symbols: Set<String>): RemoteMarketResult {
        return remoteRequestMutex.withLock {
            val now = clock.instant()
            latestRemoteBatch?.takeIf { batch ->
                val age = Duration.between(batch.completedAt, now)
                batch.requestedSymbols == symbols &&
                    !age.isNegative &&
                    age <= sharedRefreshWindow
            }?.let { batch ->
                return@withLock batch.result
            }

            val recentEntries = memoryStore.getEntries(symbols).filter { (_, entry) ->
                val age = Duration.between(entry.receivedAt, now)
                !age.isNegative &&
                    age <= sharedRefreshWindow &&
                    isValidRemoteQuote(entry.quote.symbol, entry.quote, symbols)
            }
            val pendingSymbols = symbols - recentEntries.keys
            if (pendingSymbols.isEmpty()) {
                return@withLock RemoteMarketResult(
                    requestedSymbols = symbols,
                    quotes = recentEntries.mapValuesTo(linkedMapOf()) { it.value.quote },
                    errors = emptyList()
                )
            }

            val fetched = remoteDataSource.fetchQuotes(pendingSymbols)
            val validFetchedQuotes = fetched.quotes.filter { (symbol, quote) ->
                isValidRemoteQuote(symbol, quote, pendingSymbols)
            }
            memoryStore.put(validFetchedQuotes.values)

            val merged = linkedMapOf<String, StockQuote>()
            symbols.forEach { symbol ->
                recentEntries[symbol]?.quote?.let { merged[symbol] = it }
                    ?: validFetchedQuotes[symbol]?.let { merged[symbol] = it }
            }
            val result = RemoteMarketResult(
                requestedSymbols = symbols,
                quotes = merged,
                errors = fetched.errors
            )
            latestRemoteBatch = RecentRemoteBatch(
                requestedSymbols = symbols,
                result = result,
                completedAt = clock.instant()
            )
            result
        }
    }

    private fun aggregateStatus(
        requestedSymbols: Set<String>,
        quotes: Map<String, StockQuote>,
        hasError: Boolean
    ): MarketDataStatus {
        return when {
            quotes.isEmpty() -> MarketDataStatus.ERROR
            quotes.values.any { it.dataStatus == MarketDataStatus.MOCK } -> MarketDataStatus.MOCK
            quotes.values.any { it.dataStatus == MarketDataStatus.DELAYED } -> MarketDataStatus.DELAYED
            quotes.keys == requestedSymbols &&
                quotes.values.all { it.dataStatus == MarketDataStatus.SUCCESS } -> {
                MarketDataStatus.SUCCESS
            }
            hasError -> MarketDataStatus.ERROR
            else -> MarketDataStatus.ERROR
        }
    }

    private fun isValidRemoteQuote(
        symbol: String,
        quote: StockQuote,
        requestedSymbols: Set<String>
    ): Boolean {
        return symbol in requestedSymbols &&
            quote.symbol == symbol &&
            quote.source.sourceType == MarketSourceType.REMOTE &&
            (quote.dataStatus == MarketDataStatus.SUCCESS ||
                quote.dataStatus == MarketDataStatus.DELAYED)
    }

    private fun isValidFallback(
        symbol: String,
        quote: StockQuote,
        requestedSymbols: Set<String>
    ): Boolean {
        return symbol in requestedSymbols &&
            quote.symbol == symbol &&
            quote.dataStatus == MarketDataStatus.MOCK &&
            !quote.source.supportsRealtime &&
            (quote.source.sourceType == MarketSourceType.FALLBACK ||
                quote.source.sourceType == MarketSourceType.STATIC_SAMPLE)
    }

    private fun buildRemoteError(
        requestedSymbols: Set<String>,
        missingSymbols: Set<String>,
        remoteErrors: List<RemoteMarketError>
    ): MarketDataError? {
        val directlyAffected = remoteErrors.mapNotNull { it.symbol }
            .filter { it in requestedSymbols }
            .toSet()
        val affectedSymbols = missingSymbols + directlyAffected
        if (affectedSymbols.isEmpty()) return null

        val providerIds = remoteErrors.mapNotNull { it.providerId }.distinct()
        val retryable = remoteErrors.isEmpty() || remoteErrors.any { it.isRetryable() }
        return MarketDataError(
            code = if (affectedSymbols == requestedSymbols) {
                "MARKET_REMOTE_FAILURE"
            } else {
                "MARKET_REMOTE_PARTIAL_FAILURE"
            },
            message = if (affectedSymbols == requestedSymbols) {
                "远程行情请求失败"
            } else {
                "部分远程行情缺失或解析失败"
            },
            retryable = retryable,
            affectedSymbols = affectedSymbols,
            providerId = providerIds.singleOrNull()
        )
    }

    private fun incompleteError(affectedSymbols: Set<String>): MarketDataError {
        return MarketDataError(
            code = "MARKET_DATA_INCOMPLETE",
            message = "行情结果不完整",
            retryable = true,
            affectedSymbols = affectedSymbols,
            providerId = null
        )
    }

    private fun Map<String, StockQuote>.toSnapshot(
        requestedSymbols: Set<String>,
        generatedAt: Instant
    ): QuoteSnapshot {
        val orderedQuotes = linkedMapOf<String, StockQuote>()
        requestedSymbols.forEach { symbol ->
            this[symbol]?.let { quote -> orderedQuotes[symbol] = quote }
        }
        val missingSymbols = requestedSymbols - orderedQuotes.keys
        val completeness = when {
            orderedQuotes.isEmpty() -> DataCompleteness.EMPTY
            missingSymbols.isEmpty() -> DataCompleteness.COMPLETE
            else -> DataCompleteness.PARTIAL
        }
        return QuoteSnapshot(
            requestedSymbols = requestedSymbols,
            quotes = orderedQuotes,
            missingSymbols = missingSymbols,
            completeness = completeness,
            generatedAt = generatedAt
        )
    }

    private fun RemoteMarketError.isRetryable(): Boolean = when (this) {
        is RemoteMarketError.NetworkFailure -> true
        is RemoteMarketError.HttpFailure -> statusCode == 408 || statusCode == 429 || statusCode >= 500
        is RemoteMarketError.EmptyResponse -> true
        is RemoteMarketError.MalformedResponse,
        is RemoteMarketError.InvalidField -> false
    }

    private data class RecentRemoteBatch(
        val requestedSymbols: Set<String>,
        val result: RemoteMarketResult,
        val completedAt: Instant
    )

    companion object {
        fun createDefault(): DefaultMarketDataRepository {
            return DefaultMarketDataRepository(
                remoteDataSource = TencentRemoteMarketDataSource(),
                memoryStore = QuoteMemoryStore(),
                fallbackDataSource = StaticFallbackMarketDataSource()
            )
        }
    }
}
