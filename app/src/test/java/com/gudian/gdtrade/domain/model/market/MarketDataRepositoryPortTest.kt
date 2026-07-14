package com.gudian.gdtrade.domain.model.market

import com.gudian.gdtrade.domain.repository.MarketDataRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketDataRepositoryPortTest {
    @Test
    fun `新端口支持单只批量和主动刷新`() = runBlocking {
        val repository: MarketDataRepository = FakeMarketDataRepository()
        val singleRequest = SingleQuoteRequest(
            symbol = "002185",
            policy = FetchPolicy.NETWORK_FIRST,
            maxAge = Duration.ofMinutes(1)
        )
        val batchRequest = QuoteRequest(
            symbols = setOf("002185"),
            policy = FetchPolicy.NETWORK_FIRST,
            maxAge = Duration.ofMinutes(1),
            reason = QuoteRequestReason.WATCHLIST
        )

        val single = repository.observeQuote(singleRequest).first()
        val batch = repository.observeQuotes(batchRequest).first()
        val refresh = repository.refreshQuotes(batchRequest)

        assertEquals(MarketDataStatus.MOCK, single.status)
        assertEquals(setOf("002185"), batch.data?.requestedSymbols)
        assertEquals(setOf("002185"), refresh.refreshedSymbols)
    }

    private class FakeMarketDataRepository : MarketDataRepository {
        private val quote = StockQuote(
            symbol = "002185",
            name = "华天科技",
            lastPrice = 24.62,
            changePercent = null,
            volume = null,
            turnoverAmount = null,
            turnoverRate = null,
            updatedAt = null,
            dataStatus = MarketDataStatus.MOCK,
            source = MarketSourceInfo(
                providerId = "STATIC_SAMPLE",
                sourceType = MarketSourceType.STATIC_SAMPLE,
                supportsRealtime = false,
                latency = null,
                description = "接口编译测试静态样例，非实时行情",
                receivedAt = Instant.parse("2026-07-14T02:31:00Z")
            )
        )

        override fun observeQuote(
            request: SingleQuoteRequest
        ): Flow<MarketDataState<StockQuote>> {
            return flowOf(MarketDataState(MarketDataStatus.MOCK, quote))
        }

        override fun observeQuotes(
            request: QuoteRequest
        ): Flow<MarketDataState<QuoteSnapshot>> {
            return flowOf(
                MarketDataState(
                    status = MarketDataStatus.MOCK,
                    data = QuoteSnapshot(
                        requestedSymbols = request.symbols,
                        quotes = mapOf(quote.symbol to quote),
                        missingSymbols = emptySet(),
                        completeness = DataCompleteness.COMPLETE,
                        generatedAt = Instant.parse("2026-07-14T02:31:00Z")
                    )
                )
            )
        }

        override suspend fun refreshQuotes(request: QuoteRequest): RefreshMarketResult {
            return RefreshMarketResult(
                requestedSymbols = request.symbols,
                refreshedSymbols = request.symbols,
                failedSymbols = emptySet(),
                status = MarketDataStatus.MOCK
            )
        }
    }
}