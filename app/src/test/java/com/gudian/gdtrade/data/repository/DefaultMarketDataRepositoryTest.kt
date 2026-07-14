package com.gudian.gdtrade.data.repository

import com.gudian.gdtrade.data.cache.QuoteMemoryStore
import com.gudian.gdtrade.data.local.datasource.PortfolioLocalDataSource
import com.gudian.gdtrade.data.local.datasource.WatchlistLocalDataSource
import com.gudian.gdtrade.data.remote.market.MarketHttpClient
import com.gudian.gdtrade.data.remote.market.MarketHttpResponse
import com.gudian.gdtrade.data.remote.market.RemoteMarketDataSource
import com.gudian.gdtrade.data.remote.market.RemoteMarketResult
import com.gudian.gdtrade.data.remote.market.TencentRemoteMarketDataSource
import com.gudian.gdtrade.data.remote.market.error.RemoteMarketError
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord
import com.gudian.gdtrade.domain.model.market.DataCompleteness
import com.gudian.gdtrade.domain.model.market.FetchPolicy
import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.MarketSourceInfo
import com.gudian.gdtrade.domain.model.market.MarketSourceType
import com.gudian.gdtrade.domain.model.market.QuoteRequest
import com.gudian.gdtrade.domain.model.market.QuoteRequestReason
import com.gudian.gdtrade.domain.model.market.StockQuote
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultMarketDataRepositoryTest {
    private val initialTime = Instant.parse("2026-07-14T02:31:00Z")
    private val clock = MutableClock(initialTime)

    @Test
    fun `远程全部成功时不调用 fallback`() = runTest {
        val remote = RecordingRemoteDataSource { symbols ->
            remoteResult(symbols, symbols.associateWith { successQuote(it) })
        }
        val fallback = RecordingFallbackDataSource { symbols ->
            symbols.associateWith { mockQuote(it) }
        }
        val repository = createRepository(remote, fallback)

        val states = repository.observeQuotes(request(SYMBOLS)).toList()
        val finalState = states.last()

        assertEquals(MarketDataStatus.LOADING, states.first().status)
        assertEquals(MarketDataStatus.SUCCESS, finalState.status)
        assertEquals(DataCompleteness.COMPLETE, finalState.data?.completeness)
        assertEquals(SYMBOLS, finalState.data?.quotes?.keys)
        assertTrue(fallback.requests.isEmpty())
    }

    @Test
    fun `远程全部失败时只能返回结构化错误或明确 Mock`() = runTest {
        val remote = RecordingRemoteDataSource { symbols -> failedRemoteResult(symbols) }
        val fallback = RecordingFallbackDataSource { symbols ->
            symbols.associateWith { mockQuote(it) }
        }
        val repository = createRepository(remote, fallback)

        val finalState = repository.observeQuotes(request(SYMBOLS)).toList().last()

        assertEquals(MarketDataStatus.MOCK, finalState.status)
        assertNotEquals(MarketDataStatus.SUCCESS, finalState.status)
        assertEquals(SYMBOLS, finalState.data?.quotes?.keys)
        assertTrue(finalState.data?.quotes?.values?.all { it.dataStatus == MarketDataStatus.MOCK } == true)
        assertNotNull(finalState.error)
    }

    @Test
    fun `远程部分缺失时 fallback 只请求缺失代码`() = runTest {
        val remoteSymbols = linkedSetOf("002185", "000725")
        val remote = RecordingRemoteDataSource { symbols ->
            remoteResult(symbols, remoteSymbols.associateWith { successQuote(it) })
        }
        val fallback = RecordingFallbackDataSource { symbols ->
            symbols.associateWith { mockQuote(it) }
        }
        val repository = createRepository(remote, fallback)

        val finalState = repository.observeQuotes(request(SYMBOLS)).toList().last()

        assertEquals(listOf(setOf("515070")), fallback.requests)
        assertEquals(MarketDataStatus.MOCK, finalState.status)
        assertEquals(MarketDataStatus.SUCCESS, finalState.data?.quotes?.get("002185")?.dataStatus)
        assertEquals(MarketDataStatus.SUCCESS, finalState.data?.quotes?.get("000725")?.dataStatus)
        assertEquals(MarketDataStatus.MOCK, finalState.data?.quotes?.get("515070")?.dataStatus)
    }

    @Test
    fun `远程成功结果不能被 Mock 覆盖`() = runTest {
        val remoteQuote = successQuote("002185").copy(lastPrice = 88.88)
        val remote = RecordingRemoteDataSource { symbols ->
            remoteResult(symbols, mapOf("002185" to remoteQuote))
        }
        val fallback = RecordingFallbackDataSource { symbols ->
            symbols.associateWith { mockQuote(it).copy(lastPrice = 1.0) }
        }
        val repository = createRepository(remote, fallback)

        val finalState = repository.observeQuotes(request(setOf("002185", "515070"))).toList().last()

        assertEquals(setOf("515070"), fallback.requests.single())
        assertEquals(88.88, finalState.data?.quotes?.get("002185")?.lastPrice!!, 0.000001)
        assertEquals(MarketDataStatus.SUCCESS, finalState.data?.quotes?.get("002185")?.dataStatus)
    }

    @Test
    fun `远程与 Mock 混合时保留逐条状态且批次为 Mock`() = runTest {
        val remote = RecordingRemoteDataSource { symbols ->
            remoteResult(symbols, mapOf("002185" to delayedQuote("002185")))
        }
        val fallback = RecordingFallbackDataSource { symbols ->
            symbols.associateWith { mockQuote(it) }
        }
        val repository = createRepository(remote, fallback)

        val finalState = repository.observeQuotes(request(setOf("002185", "515070"))).toList().last()

        assertEquals(MarketDataStatus.MOCK, finalState.status)
        assertEquals(MarketDataStatus.DELAYED, finalState.data?.quotes?.get("002185")?.dataStatus)
        assertEquals(MarketDataStatus.MOCK, finalState.data?.quotes?.get("515070")?.dataStatus)
        assertEquals(MarketSourceType.REMOTE, finalState.data?.quotes?.get("002185")?.source?.sourceType)
        assertEquals(MarketSourceType.FALLBACK, finalState.data?.quotes?.get("515070")?.source?.sourceType)
    }

    @Test
    fun `DELAYED 必须传播到批次状态`() = runTest {
        val remote = RecordingRemoteDataSource { symbols ->
            remoteResult(symbols, symbols.associateWith { delayedQuote(it) })
        }
        val fallback = RecordingFallbackDataSource { emptyMap() }
        val repository = createRepository(remote, fallback)

        val finalState = repository.observeQuotes(request(SYMBOLS)).toList().last()

        assertEquals(MarketDataStatus.DELAYED, finalState.status)
        assertTrue(finalState.data?.quotes?.values?.all { it.dataStatus == MarketDataStatus.DELAYED } == true)
    }

    @Test
    fun `刷新失败时旧有效缓存继续存在`() = runTest {
        var shouldFail = false
        val store = QuoteMemoryStore()
        val remote = RecordingRemoteDataSource { symbols ->
            if (shouldFail) failedRemoteResult(symbols) else {
                remoteResult(symbols, symbols.associateWith { successQuote(it) })
            }
        }
        val repository = createRepository(
            remote = remote,
            fallback = RecordingFallbackDataSource { emptyMap() },
            store = store
        )
        repository.observeQuotes(request(setOf("002185"))).toList()
        val cachedBeforeFailure = store.get("002185")
        shouldFail = true
        clock.advance(Duration.ofSeconds(2))

        val refresh = repository.refreshQuotes(request(setOf("002185")))

        assertEquals(MarketDataStatus.ERROR, refresh.status)
        assertEquals(setOf("002185"), refresh.failedSymbols)
        assertEquals(cachedBeforeFailure, store.get("002185"))
    }

    @Test
    fun `QuoteSnapshot 必须准确表达请求结果缺失和完整度`() = runTest {
        val requested = linkedSetOf("002185", "515070")
        val remote = RecordingRemoteDataSource { symbols ->
            remoteResult(symbols, mapOf("002185" to successQuote("002185")))
        }
        val repository = createRepository(
            remote = remote,
            fallback = RecordingFallbackDataSource { emptyMap() }
        )

        val finalState = repository.observeQuotes(request(requested)).toList().last()
        val snapshot = finalState.data!!

        assertEquals(requested, snapshot.requestedSymbols)
        assertEquals(setOf("002185"), snapshot.quotes.keys)
        assertEquals(setOf("515070"), snapshot.missingSymbols)
        assertEquals(DataCompleteness.PARTIAL, snapshot.completeness)
        assertTrue(snapshot.quotes.keys.intersect(snapshot.missingSymbols).isEmpty())
        assertEquals(MarketDataStatus.ERROR, finalState.status)
    }

    @Test
    fun `Mock 通过旧兼容映射后永远不能标记实时`() {
        val adapter = StockQuoteLegacyAdapter()

        val legacy = adapter.toLegacy(
            quote = mockQuote("002185"),
            now = clock.instant(),
            maxAge = Duration.ofMinutes(1)
        )

        assertFalse(legacy.isRealtime)
        assertTrue(legacy.sourceLabel.contains("非实时行情"))
    }

    @Test
    fun `请求错误携带旧成功快照时旧接口仍不能标记实时`() {
        val adapter = StockQuoteLegacyAdapter()

        val legacy = adapter.toLegacy(
            quote = successQuote("002185"),
            now = clock.instant(),
            maxAge = Duration.ofMinutes(1),
            requestStatus = MarketDataStatus.ERROR
        )

        assertFalse(legacy.isRealtime)
        assertTrue(legacy.sourceLabel.contains("行情错误"))
    }

    @Test
    fun `当前腾讯来源必须保持 DELAYED`() = runTest {
        val dataSource = TencentRemoteMarketDataSource(
            httpClient = MarketHttpClient {
                MarketHttpResponse(statusCode = 200, body = "${tencentRecord()};")
            },
            clock = Clock.fixed(initialTime, ZoneId.of("UTC"))
        )

        val result = dataSource.fetchQuotes(setOf("002185"))
        val quote = result.quotes.getValue("002185")

        assertEquals(MarketDataStatus.DELAYED, quote.dataStatus)
        assertEquals("TENCENT_QT", quote.source.providerId)
        assertEquals(MarketSourceType.REMOTE, quote.source.sourceType)
        assertFalse(quote.source.supportsRealtime)
    }

    @Test
    fun `新旧兼容接口共享同一次远程刷新结果`() = runTest {
        val remote = RecordingRemoteDataSource { symbols ->
            remoteResult(symbols, symbols.associateWith { successQuote(it) })
        }
        val defaultRepository = createRepository(
            remote = remote,
            fallback = RecordingFallbackDataSource { symbols ->
                symbols.associateWith { mockQuote(it) }
            }
        )
        val roomRepository = RoomTradeRepository(
            portfolioLocalDataSource = FakePortfolioLocalDataSource(),
            watchlistLocalDataSource = FakeWatchlistLocalDataSource(),
            marketDataRepository = defaultRepository,
            clock = clock
        )
        val richRequest = request(setOf("002185"))

        roomRepository.refreshQuotes(richRequest)
        val legacyQuotes = roomRepository.observeQuotes(listOf("002185")).first()

        assertEquals(1, remote.requests.size)
        assertEquals(listOf("002185"), legacyQuotes.map { it.symbol })
    }

    @Test
    fun `同批刷新失败也必须共享远程结果`() = runTest {
        val remote = RecordingRemoteDataSource { symbols -> failedRemoteResult(symbols) }
        val repository = createRepository(
            remote = remote,
            fallback = RecordingFallbackDataSource { emptyMap() }
        )
        val richRequest = request(setOf("002185"))

        val first = repository.refreshQuotes(richRequest)
        val second = repository.refreshQuotes(richRequest)

        assertEquals(MarketDataStatus.ERROR, first.status)
        assertEquals(MarketDataStatus.ERROR, second.status)
        assertEquals(1, remote.requests.size)
    }

    @Test
    fun `同批短窗口成功刷新只请求一次远程源`() = runTest {
        val remote = RecordingRemoteDataSource { symbols ->
            remoteResult(symbols, symbols.associateWith { successQuote(it) })
        }
        val repository = createRepository(
            remote = remote,
            fallback = RecordingFallbackDataSource { emptyMap() }
        )
        val richRequest = request(setOf("002185", "000725"))

        val first = repository.refreshQuotes(richRequest)
        val second = repository.refreshQuotes(richRequest)

        assertEquals(MarketDataStatus.SUCCESS, first.status)
        assertEquals(MarketDataStatus.SUCCESS, second.status)
        assertEquals(listOf(setOf("002185", "000725")), remote.requests)
    }

    @Test
    fun `重叠批次只请求短窗口缓存尚未覆盖的代码`() = runTest {
        val remote = RecordingRemoteDataSource { symbols ->
            remoteResult(symbols, symbols.associateWith { successQuote(it) })
        }
        val repository = createRepository(
            remote = remote,
            fallback = RecordingFallbackDataSource { emptyMap() }
        )

        repository.refreshQuotes(request(linkedSetOf("002185", "000725")))
        val overlapResult = repository.refreshQuotes(request(linkedSetOf("000725", "515070")))

        assertEquals(MarketDataStatus.SUCCESS, overlapResult.status)
        assertEquals(
            listOf(setOf("002185", "000725"), setOf("515070")),
            remote.requests
        )
    }

    @Test
    fun `失败请求去重不会阻止窗口结束后的正常重试`() = runTest {
        var shouldFail = true
        val remote = RecordingRemoteDataSource { symbols ->
            if (shouldFail) {
                failedRemoteResult(symbols)
            } else {
                remoteResult(symbols, symbols.associateWith { successQuote(it) })
            }
        }
        val repository = createRepository(
            remote = remote,
            fallback = RecordingFallbackDataSource { emptyMap() }
        )
        val richRequest = request(setOf("002185"))

        val first = repository.refreshQuotes(richRequest)
        val sharedFailure = repository.refreshQuotes(richRequest)
        shouldFail = false
        clock.advance(Duration.ofSeconds(2))
        val retried = repository.refreshQuotes(richRequest)

        assertEquals(MarketDataStatus.ERROR, first.status)
        assertEquals(MarketDataStatus.ERROR, sharedFailure.status)
        assertEquals(MarketDataStatus.SUCCESS, retried.status)
        assertEquals(listOf(setOf("002185"), setOf("002185")), remote.requests)
    }

    @Test
    fun `缓存保持原状态来源且 Mock 不覆盖有效远程行情`() {
        val store = QuoteMemoryStore()
        val delayed = delayedQuote("002185")
        store.put(listOf(delayed))
        store.put(listOf(mockQuote("002185")))

        val cached = store.get("002185")

        assertEquals(delayed, cached)
        assertEquals(MarketDataStatus.DELAYED, cached?.dataStatus)
        assertEquals(MarketSourceType.REMOTE, cached?.source?.sourceType)
        assertEquals(initialTime, store.lastSuccessfulSnapshotAt())
    }

    @Test
    fun `远程 HTTP 失败必须返回结构化错误`() = runTest {
        val dataSource = TencentRemoteMarketDataSource(
            httpClient = MarketHttpClient { MarketHttpResponse(statusCode = 503, body = "") },
            clock = clock
        )

        val result = dataSource.fetchQuotes(setOf("002185"))

        assertTrue(result.quotes.isEmpty())
        assertEquals("REMOTE_HTTP_FAILURE", result.errors.single().code)
        assertEquals(MarketDataStatus.ERROR, result.errors.single().dataStatus)
    }

    private fun createRepository(
        remote: RemoteMarketDataSource,
        fallback: FallbackMarketDataSource,
        store: QuoteMemoryStore = QuoteMemoryStore()
    ): DefaultMarketDataRepository {
        return DefaultMarketDataRepository(
            remoteDataSource = remote,
            memoryStore = store,
            fallbackDataSource = fallback,
            clock = clock,
            sharedRefreshWindow = Duration.ofSeconds(1)
        )
    }

    private fun request(symbols: Set<String>): QuoteRequest {
        return QuoteRequest(
            symbols = symbols,
            policy = FetchPolicy.NETWORK_FIRST,
            maxAge = Duration.ofSeconds(30),
            reason = QuoteRequestReason.BATCH
        )
    }

    private fun remoteResult(
        requestedSymbols: Set<String>,
        quotes: Map<String, StockQuote>
    ): RemoteMarketResult {
        return RemoteMarketResult(
            requestedSymbols = requestedSymbols,
            quotes = quotes,
            errors = emptyList()
        )
    }

    private fun failedRemoteResult(symbols: Set<String>): RemoteMarketResult {
        return RemoteMarketResult(
            requestedSymbols = symbols,
            quotes = emptyMap(),
            errors = listOf(RemoteMarketError.NetworkFailure("TEST_REMOTE"))
        )
    }

    private fun successQuote(symbol: String): StockQuote {
        return StockQuote(
            symbol = symbol,
            name = quoteName(symbol),
            lastPrice = 24.62,
            changePercent = 1.2,
            volume = 35_680_000L,
            turnoverAmount = 876_400_000.0,
            turnoverRate = 2.18,
            updatedAt = clock.instant().minusSeconds(10),
            dataStatus = MarketDataStatus.SUCCESS,
            source = MarketSourceInfo(
                providerId = "VERIFIED_REMOTE",
                sourceType = MarketSourceType.REMOTE,
                supportsRealtime = true,
                latency = Duration.ofSeconds(1),
                description = "已验证测试远程行情",
                receivedAt = clock.instant()
            )
        )
    }

    private fun delayedQuote(symbol: String): StockQuote {
        return successQuote(symbol).copy(
            dataStatus = MarketDataStatus.DELAYED,
            source = successQuote(symbol).source.copy(
                providerId = "TENCENT_QT",
                supportsRealtime = false,
                latency = null,
                description = "腾讯行情接口，时效能力未验证"
            )
        )
    }

    private fun mockQuote(symbol: String): StockQuote {
        return StockQuote(
            symbol = symbol,
            name = quoteName(symbol),
            lastPrice = null,
            changePercent = null,
            volume = null,
            turnoverAmount = null,
            turnoverRate = null,
            updatedAt = null,
            dataStatus = MarketDataStatus.MOCK,
            source = MarketSourceInfo(
                providerId = "FALLBACK",
                sourceType = MarketSourceType.FALLBACK,
                supportsRealtime = false,
                latency = null,
                description = "测试静态 fallback，非实时行情",
                receivedAt = clock.instant()
            )
        )
    }

    private fun quoteName(symbol: String): String = when (symbol) {
        "002185" -> "华天科技"
        "000725" -> "京东方A"
        "515070" -> "华夏中证人工智能主题ETF"
        else -> symbol
    }

    private fun tencentRecord(): String {
        val fields = MutableList(39) { "" }
        fields[1] = "华天科技"
        fields[2] = "002185"
        fields[3] = "24.62"
        fields[6] = "356800"
        fields[30] = "20260714103000"
        fields[32] = "1.20"
        fields[37] = "87640"
        fields[38] = "2.18"
        return "v_sz002185=\"${fields.joinToString("~")}\""
    }

    private class RecordingRemoteDataSource(
        private val response: (Set<String>) -> RemoteMarketResult
    ) : RemoteMarketDataSource {
        val requests = mutableListOf<Set<String>>()

        override suspend fun fetchQuotes(symbols: Set<String>): RemoteMarketResult {
            requests += symbols.toSet()
            return response(symbols)
        }
    }

    private class RecordingFallbackDataSource(
        private val response: (Set<String>) -> Map<String, StockQuote>
    ) : FallbackMarketDataSource {
        val requests = mutableListOf<Set<String>>()

        override suspend fun getQuotes(symbols: Set<String>): Map<String, StockQuote> {
            requests += symbols.toSet()
            return response(symbols)
        }
    }

    private class MutableClock(
        private var current: Instant
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

    private class FakePortfolioLocalDataSource : PortfolioLocalDataSource {
        override fun observePositions(): Flow<List<Position>> = flowOf(
            listOf(Position("002185", "华天科技", 200, "兼容测试"))
        )

        override fun observeTradeRecords(): Flow<List<TradeRecord>> = flowOf(emptyList())

        override suspend fun upsertPosition(position: Position) = Unit

        override suspend fun deletePosition(symbol: String) = Unit

        override suspend fun insertTradeRecord(record: TradeRecord) = Unit

        override suspend fun deleteTradeRecord(recordKey: String) = Unit

        override suspend fun resetPortfolioData() = Unit
    }

    private class FakeWatchlistLocalDataSource : WatchlistLocalDataSource {
        override fun observeCandidates(): Flow<List<StockCandidate>> = flowOf(emptyList())

        override suspend fun upsertCandidate(candidate: StockCandidate) = Unit

        override suspend fun deleteCandidate(symbol: String) = Unit

        override suspend fun resetWatchlist() = Unit
    }

    private companion object {
        val SYMBOLS: Set<String> = linkedSetOf("002185", "000725", "515070")
    }
}
