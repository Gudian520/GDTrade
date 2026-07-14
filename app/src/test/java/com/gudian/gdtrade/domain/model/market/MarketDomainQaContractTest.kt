package com.gudian.gdtrade.domain.model.market

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketDomainQaContractTest {
    private val receivedAt = Instant.parse("2026-07-14T02:31:00Z")
    private val updatedAt = Instant.parse("2026-07-14T02:30:00Z")

    @Test
    fun `状态枚举必须与 QA 基线完全一致`() {
        assertEquals(
            listOf("SUCCESS", "LOADING", "ERROR", "DELAYED", "MOCK"),
            MarketDataStatus.entries.map { it.name }
        )
    }

    @Test
    fun `正常行情必须暴露完整字段和 Instant 时间`() {
        val quote = successQuote()
        val quoteTime: Instant? = quote.updatedAt
        val sourceReceivedAt: Instant = quote.source.receivedAt

        assertEquals("002185", quote.symbol)
        assertEquals("华天科技", quote.name)
        assertNotNull(quote.lastPrice)
        assertNotNull(quote.changePercent)
        assertNotNull(quote.volume)
        assertNotNull(quote.turnoverAmount)
        assertNotNull(quote.turnoverRate)
        assertEquals(updatedAt, quoteTime)
        assertEquals(receivedAt, sourceReceivedAt)
        assertEquals(MarketDataStatus.SUCCESS, quote.dataStatus)
        assertEquals(MarketSourceType.REMOTE, quote.source.sourceType)
    }

    @Test
    fun `未知行情值必须使用 null`() {
        val quote = successQuote().copy(
            lastPrice = null,
            changePercent = null,
            volume = null,
            turnoverAmount = null,
            turnoverRate = null,
            updatedAt = null,
            dataStatus = MarketDataStatus.ERROR,
            source = successQuote().source.copy(supportsRealtime = false)
        )

        assertNull(quote.lastPrice)
        assertNull(quote.changePercent)
        assertNull(quote.volume)
        assertNull(quote.turnoverAmount)
        assertNull(quote.turnoverRate)
        assertNull(quote.updatedAt)
    }

    @Test
    fun `Mock 来源不得支持实时`() {
        val source = MarketSourceInfo(
            providerId = "STATIC_SAMPLE",
            sourceType = MarketSourceType.STATIC_SAMPLE,
            supportsRealtime = false,
            latency = null,
            description = "静态样例，非实时行情",
            receivedAt = receivedAt
        )
        val quote = successQuote().copy(
            updatedAt = null,
            dataStatus = MarketDataStatus.MOCK,
            source = source
        )

        assertEquals(MarketDataStatus.MOCK, quote.dataStatus)
        assertFalse(quote.source.supportsRealtime)
        assertEquals(MarketSourceType.STATIC_SAMPLE, quote.source.sourceType)
        assertThrows(IllegalArgumentException::class.java) {
            source.copy(supportsRealtime = true)
        }
    }

    @Test
    fun `Error 状态不能形成有效交易信号`() {
        val error = MarketDataError(
            code = "REMOTE_ERROR",
            message = "远程行情不可用",
            retryable = true,
            affectedSymbols = setOf("002185"),
            providerId = "TEST_REMOTE"
        )
        val state = MarketDataState<StockQuote>(
            status = MarketDataStatus.ERROR,
            data = null,
            error = error
        )

        val canGenerateTradingSignal =
            state.status == MarketDataStatus.SUCCESS && state.data != null

        assertFalse(canGenerateTradingSignal)
        assertEquals(setOf("002185"), state.error?.affectedSymbols)
    }

    @Test
    fun `只有成功且来源支持实时的数据才满足实时前置条件`() {
        val success = successQuote()
        val delayed = success.copy(dataStatus = MarketDataStatus.DELAYED)
        val unsupported = success.copy(source = success.source.copy(supportsRealtime = false))

        assertTrue(success.hasRealtimePrerequisites())
        assertFalse(delayed.hasRealtimePrerequisites())
        assertFalse(unsupported.hasRealtimePrerequisites())
    }

    private fun successQuote(): StockQuote {
        return StockQuote(
            symbol = "002185",
            name = "华天科技",
            lastPrice = 24.62,
            changePercent = 1.20,
            volume = 35_680_000,
            turnoverAmount = 876_400_000.0,
            turnoverRate = 2.18,
            updatedAt = updatedAt,
            dataStatus = MarketDataStatus.SUCCESS,
            source = MarketSourceInfo(
                providerId = "VERIFIED_REMOTE",
                sourceType = MarketSourceType.REMOTE,
                supportsRealtime = true,
                latency = Duration.ZERO,
                description = "已验证远程测试源",
                receivedAt = receivedAt
            )
        )
    }

    private fun StockQuote.hasRealtimePrerequisites(): Boolean {
        return dataStatus == MarketDataStatus.SUCCESS &&
            source.supportsRealtime &&
            updatedAt != null
    }
}