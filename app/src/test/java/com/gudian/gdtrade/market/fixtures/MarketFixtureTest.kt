package com.gudian.gdtrade.market.fixtures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketFixtureTest {
    @Test
    fun `正常行情包含完整字段、更新时间和来源`() {
        val quote = MarketContractFixtures.successQuote

        assertEquals("002185", quote.symbol)
        assertEquals("华天科技", quote.name)
        assertNotNull(quote.lastPrice)
        assertNotNull(quote.changePercent)
        assertNotNull(quote.volume)
        assertNotNull(quote.turnoverAmount)
        assertNotNull(quote.turnoverRate)
        assertNotNull(quote.updatedAt)
        assertEquals(FixtureMarketDataStatus.SUCCESS, quote.dataStatus)
        assertEquals(FixtureMarketSourceType.REMOTE, quote.source.sourceType)
    }

    @Test
    fun `缺失行情值使用 null 而不使用零值伪造`() {
        val quote = MarketContractFixtures.errorQuote

        assertNull(quote.lastPrice)
        assertNull(quote.changePercent)
        assertNull(quote.updatedAt)
        assertEquals(FixtureMarketDataStatus.ERROR, quote.dataStatus)
    }

    @Test
    fun `Mock 与延迟行情均不能标记实时`() {
        val quotes = listOf(
            MarketContractFixtures.mockQuote,
            MarketContractFixtures.delayedQuote
        )

        assertTrue(quotes.all { !it.source.supportsRealtime })
        assertEquals("STATIC_SAMPLE", MarketContractFixtures.mockQuote.source.providerId)
        assertEquals(
            FixtureMarketSourceType.STATIC_SAMPLE,
            MarketContractFixtures.mockQuote.source.sourceType
        )
    }

    @Test
    fun `错误、加载、延迟和 Mock 状态不能生成有效交易信号`() {
        val unsafeQuotes = listOf(
            MarketContractFixtures.errorQuote,
            MarketContractFixtures.loadingQuote,
            MarketContractFixtures.delayedQuote,
            MarketContractFixtures.mockQuote
        )

        assertTrue(unsafeQuotes.none { it.canGenerateTradingSignal })
        assertFalse(MarketContractFixtures.errorQuote.source.supportsRealtime)
    }
}
