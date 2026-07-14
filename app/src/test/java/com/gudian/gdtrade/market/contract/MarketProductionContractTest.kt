package com.gudian.gdtrade.market.contract

import com.gudian.gdtrade.data.cache.QuoteMemoryStore
import com.gudian.gdtrade.data.remote.market.RemoteMarketDataSource
import com.gudian.gdtrade.data.remote.market.RemoteMarketResult
import com.gudian.gdtrade.data.remote.market.error.RemoteMarketError
import com.gudian.gdtrade.data.remote.market.mapper.QuoteMapper
import com.gudian.gdtrade.data.remote.market.mapper.RemoteQuoteMappingResult
import com.gudian.gdtrade.data.remote.market.parser.QuoteParser
import com.gudian.gdtrade.data.repository.DefaultMarketDataRepository
import com.gudian.gdtrade.data.repository.FallbackMarketDataSource
import com.gudian.gdtrade.data.repository.StockQuoteLegacyAdapter
import com.gudian.gdtrade.domain.model.market.FetchPolicy
import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.MarketSourceInfo
import com.gudian.gdtrade.domain.model.market.MarketSourceType
import com.gudian.gdtrade.domain.model.market.QuoteRequest
import com.gudian.gdtrade.domain.model.market.QuoteRequestReason
import com.gudian.gdtrade.domain.model.market.StockQuote
import com.gudian.gdtrade.market.fixtures.RemoteMarketFixtureLoader
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/** 将 QA StockQuote 抽象契约绑定到 market-domain-v1 生产模型。 */
class StockQuoteProductionContractTest : StockQuoteContractTest() {
    override val subject: StockQuoteContractSubject = object : StockQuoteContractSubject {
        override fun successQuote(): StockQuoteContractView = fullQuote().toContractView()

        override fun quoteWithMissingValues(): StockQuoteContractView {
            return fullQuote().copy(
                lastPrice = null,
                changePercent = null,
                updatedAt = null,
                dataStatus = MarketDataStatus.DELAYED,
                source = remoteSource(supportsRealtime = false)
            ).toContractView()
        }

        override fun quoteWithUnknownUpdateTime(): StockQuoteContractView {
            return fullQuote().copy(
                updatedAt = null,
                dataStatus = MarketDataStatus.DELAYED,
                source = remoteSource(supportsRealtime = false)
            ).toContractView()
        }

        override fun mockQuote(): StockQuoteContractView {
            return fullQuote().copy(
                updatedAt = null,
                dataStatus = MarketDataStatus.MOCK,
                source = MarketSourceInfo(
                    providerId = "STATIC_SAMPLE",
                    sourceType = MarketSourceType.STATIC_SAMPLE,
                    supportsRealtime = false,
                    latency = null,
                    description = "静态样例，非实时行情",
                    receivedAt = RECEIVED_AT
                )
            ).toContractView()
        }
    }
}

/** 使用腾讯 Parser 与 QuoteMapper 激活 QA Remote 契约。 */
class TencentRemoteParserProductionContractTest : RemoteMarketParserContractTest() {
    override val parser: RemoteMarketParserContractSubject = object : RemoteMarketParserContractSubject {
        private val quoteParser = QuoteParser()
        private val quoteMapper = QuoteMapper()

        override fun parse(rawResponse: String): ParserContractResult {
            val parsed = quoteParser.parseTencent(rawResponse)
            if (parsed.quotes.isEmpty()) {
                return if (parsed.errors.any { it is RemoteMarketError.EmptyResponse }) {
                    ParserContractResult.Empty
                } else {
                    ParserContractResult.Invalid(parsed.errors.joinToString { it.code })
                }
            }

            val mapped = parsed.quotes.map { quoteMapper.map(it, RECEIVED_AT) }
            val failures = mapped.filterIsInstance<RemoteQuoteMappingResult.Failure>()
            if (parsed.errors.isNotEmpty() || failures.isNotEmpty()) {
                val reasons = parsed.errors.map { it.code } + failures.map { it.error.code }
                return ParserContractResult.Invalid(reasons.joinToString())
            }

            val quotes = mapped.filterIsInstance<RemoteQuoteMappingResult.Success>().map { result ->
                ParsedQuoteView(
                    symbol = result.quote.symbol,
                    name = result.quote.name,
                    lastPrice = result.quote.lastPrice
                        ?: return ParserContractResult.Invalid("REMOTE_PRICE_MISSING")
                )
            }
            return ParserContractResult.Success(quotes)
        }
    }

    override fun readFixture(fileName: String): String {
        return when (fileName) {
            "normal_response.json" -> RemoteMarketFixtureLoader.read("tencent_normal_response.txt")
            "empty_response.json" -> ""
            "missing_fields.json" -> RemoteMarketFixtureLoader.read("tencent_missing_fields.txt")
            "malformed_response.txt" -> RemoteMarketFixtureLoader.read("tencent_malformed_response.txt")
            else -> error("未知腾讯契约 fixture：$fileName")
        }
    }
}

/** 将 QA Repository 组合契约绑定到 DefaultMarketDataRepository。 */
class DefaultMarketDataRepositoryProductionContractTest : MarketRepositoryContractTest() {
    override val driver: MarketRepositoryContractDriver = ProductionRepositoryDriver()
}

private class ProductionRepositoryDriver : MarketRepositoryContractDriver {
    override fun execute(scenario: RepositoryScenario): RepositoryContractObservation = runBlocking {
        val requestedSymbols = when (scenario) {
            RepositoryScenario.PARTIAL_REMOTE_SUCCESS -> linkedSetOf("002185", "000725")
            else -> linkedSetOf("002185")
        }
        val remote = RecordingRemoteDataSource { symbols ->
            when (scenario) {
                RepositoryScenario.REMOTE_SUCCESS -> RemoteMarketResult(
                    requestedSymbols = symbols,
                    quotes = symbols.associateWith(::fullQuote),
                    errors = emptyList()
                )
                RepositoryScenario.PARTIAL_REMOTE_SUCCESS -> RemoteMarketResult(
                    requestedSymbols = symbols,
                    quotes = mapOf("002185" to fullQuote("002185")),
                    errors = listOf(RemoteMarketError.InvalidField(
                        providerId = "TEST_REMOTE",
                        symbol = "000725",
                        fieldName = "lastPrice",
                        reason = "测试字段缺失"
                    ))
                )
                RepositoryScenario.REMOTE_ERROR_WITH_FALLBACK,
                RepositoryScenario.REMOTE_ERROR_WITHOUT_FALLBACK -> RemoteMarketResult(
                    requestedSymbols = symbols,
                    quotes = emptyMap(),
                    errors = listOf(RemoteMarketError.NetworkFailure("TEST_REMOTE"))
                )
            }
        }
        val fallback = RecordingFallbackDataSource { symbols ->
            if (scenario == RepositoryScenario.REMOTE_ERROR_WITHOUT_FALLBACK) {
                emptyMap()
            } else {
                symbols.associateWith(::mockQuote)
            }
        }
        val repository = DefaultMarketDataRepository(
            remoteDataSource = remote,
            memoryStore = QuoteMemoryStore(),
            fallbackDataSource = fallback,
            clock = FIXED_CLOCK,
            sharedRefreshWindow = Duration.ofSeconds(1)
        )
        val request = QuoteRequest(
            symbols = requestedSymbols,
            policy = FetchPolicy.NETWORK_FIRST,
            maxAge = Duration.ofSeconds(30),
            reason = QuoteRequestReason.BATCH
        )

        val state = repository.observeQuotes(request).toList().last()
        val quotes = state.data?.quotes.orEmpty()
        val realtimeSymbols = quotes.filterValues { quote ->
            StockQuoteLegacyAdapter().toLegacy(
                quote = quote,
                now = RECEIVED_AT,
                maxAge = Duration.ofSeconds(30),
                requestStatus = state.status
            ).isRealtime
        }.keys

        RepositoryContractObservation(
            batchStatus = state.status.name,
            remoteCallCount = remote.requests.size,
            fallbackCallCount = fallback.requests.size,
            fallbackRequestedSymbols = fallback.requests.flatten().toSet(),
            missingSymbols = state.data?.missingSymbols ?: requestedSymbols,
            mockSymbols = quotes.filterValues { it.dataStatus == MarketDataStatus.MOCK }.keys,
            realtimeSymbols = realtimeSymbols,
            quotesBySymbol = quotes.mapValues { it.value.dataStatus.name }
        )
    }
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

private fun fullQuote(symbol: String = "002185"): StockQuote {
    return StockQuote(
        symbol = symbol,
        name = if (symbol == "002185") "华天科技" else "京东方A",
        lastPrice = 24.62,
        changePercent = 1.20,
        volume = 35_680_000,
        turnoverAmount = 876_400_000.0,
        turnoverRate = 2.18,
        updatedAt = UPDATED_AT,
        dataStatus = MarketDataStatus.SUCCESS,
        source = remoteSource(supportsRealtime = true)
    )
}

private fun mockQuote(symbol: String): StockQuote {
    return fullQuote(symbol).copy(
        updatedAt = null,
        dataStatus = MarketDataStatus.MOCK,
        source = MarketSourceInfo(
            providerId = "FALLBACK",
            sourceType = MarketSourceType.FALLBACK,
            supportsRealtime = false,
            latency = null,
            description = "测试 fallback，非实时行情",
            receivedAt = RECEIVED_AT
        )
    )
}

private fun remoteSource(supportsRealtime: Boolean): MarketSourceInfo {
    return MarketSourceInfo(
        providerId = if (supportsRealtime) "VERIFIED_REMOTE" else "TENCENT_QT",
        sourceType = MarketSourceType.REMOTE,
        supportsRealtime = supportsRealtime,
        latency = if (supportsRealtime) Duration.ZERO else null,
        description = if (supportsRealtime) "已验证远程测试源" else "腾讯行情接口，时效能力未验证",
        receivedAt = RECEIVED_AT
    )
}

private fun StockQuote.toContractView(): StockQuoteContractView {
    return StockQuoteContractView(
        symbol = symbol,
        name = name,
        lastPrice = lastPrice,
        changePercent = changePercent,
        volume = volume,
        turnoverAmount = turnoverAmount,
        turnoverRate = turnoverRate,
        updatedAt = updatedAt,
        status = dataStatus.name,
        providerId = source.providerId,
        sourceType = source.sourceType.name,
        supportsRealtime = source.supportsRealtime
    )
}

private val RECEIVED_AT: Instant = Instant.parse("2026-07-14T02:31:00Z")
private val UPDATED_AT: Instant = Instant.parse("2026-07-14T02:30:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(RECEIVED_AT, ZoneOffset.UTC)
