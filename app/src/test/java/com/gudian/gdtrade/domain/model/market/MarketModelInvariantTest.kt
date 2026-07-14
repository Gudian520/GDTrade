package com.gudian.gdtrade.domain.model.market

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MarketModelInvariantTest {
    private val now = Instant.parse("2026-07-14T02:31:00Z")

    @Test
    fun `批量请求拒绝空代码集合和负缓存时间`() {
        assertThrows(IllegalArgumentException::class.java) {
            QuoteRequest(
                symbols = emptySet(),
                policy = FetchPolicy.NETWORK_FIRST,
                maxAge = Duration.ofSeconds(30),
                reason = QuoteRequestReason.PORTFOLIO
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SingleQuoteRequest(
                symbol = "002185",
                policy = FetchPolicy.NETWORK_FIRST,
                maxAge = Duration.ofSeconds(-1)
            )
        }
    }

    @Test
    fun `行情快照必须保持代码和完整度一致`() {
        val quote = sampleQuote()
        val snapshot = QuoteSnapshot(
            requestedSymbols = setOf("002185", "000725"),
            quotes = mapOf("002185" to quote),
            missingSymbols = setOf("000725"),
            completeness = DataCompleteness.PARTIAL,
            generatedAt = now
        )

        assertEquals(setOf("000725"), snapshot.missingSymbols)
        assertEquals(DataCompleteness.PARTIAL, snapshot.completeness)
        assertThrows(IllegalArgumentException::class.java) {
            snapshot.copy(completeness = DataCompleteness.COMPLETE)
        }
    }

    @Test
    fun `Error 状态和刷新结果必须包含错误信息`() {
        assertThrows(IllegalArgumentException::class.java) {
            MarketDataState<StockQuote>(
                status = MarketDataStatus.ERROR,
                data = null
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RefreshMarketResult(
                requestedSymbols = setOf("002185"),
                refreshedSymbols = emptySet(),
                failedSymbols = setOf("002185"),
                status = MarketDataStatus.ERROR
            )
        }
    }

    @Test
    fun `非远程来源不能声明支持实时`() {
        assertThrows(IllegalArgumentException::class.java) {
            MarketSourceInfo(
                providerId = "LOCAL_CACHE",
                sourceType = MarketSourceType.LOCAL_CACHE,
                supportsRealtime = true,
                latency = Duration.ZERO,
                description = "本地缓存",
                receivedAt = now
            )
        }
    }

    @Test
    fun `股票代码和非负行情数值必须有效`() {
        assertThrows(IllegalArgumentException::class.java) {
            sampleQuote().copy(symbol = "sh002185")
        }
        assertThrows(IllegalArgumentException::class.java) {
            sampleQuote().copy(lastPrice = -1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            sampleQuote().copy(volume = -1)
        }
    }

    private fun sampleQuote(): StockQuote {
        return StockQuote(
            symbol = "002185",
            name = "华天科技",
            lastPrice = 24.62,
            changePercent = 1.20,
            volume = 35_680_000,
            turnoverAmount = 876_400_000.0,
            turnoverRate = 2.18,
            updatedAt = Instant.parse("2026-07-14T02:30:00Z"),
            dataStatus = MarketDataStatus.SUCCESS,
            source = MarketSourceInfo(
                providerId = "VERIFIED_REMOTE",
                sourceType = MarketSourceType.REMOTE,
                supportsRealtime = true,
                latency = Duration.ZERO,
                description = "已验证远程测试源",
                receivedAt = now
            )
        )
    }
}