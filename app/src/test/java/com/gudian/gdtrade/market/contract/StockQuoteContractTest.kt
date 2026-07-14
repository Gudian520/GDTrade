package com.gudian.gdtrade.market.contract

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Developer 实现 StockQuote 后，通过适配器继承本契约，无需复制断言。 */
abstract class StockQuoteContractTest {
    protected abstract val subject: StockQuoteContractSubject

    @Test
    fun `StockQuote 必须暴露完整行情字段`() {
        val quote = subject.successQuote()

        assertEquals("002185", quote.symbol)
        assertEquals("华天科技", quote.name)
        assertNotNull(quote.lastPrice)
        assertNotNull(quote.changePercent)
        assertNotNull(quote.volume)
        assertNotNull(quote.turnoverAmount)
        assertNotNull(quote.turnoverRate)
        assertNotNull(quote.updatedAt)
        assertNotNull(quote.providerId)
        assertNotNull(quote.sourceType)
    }

    @Test
    fun `不可解析字段必须保留为 null`() {
        val quote = subject.quoteWithMissingValues()

        assertNull(quote.lastPrice)
        assertNull(quote.changePercent)
        assertNull(quote.updatedAt)
    }

    @Test
    fun `未知更新时间不得用本机时间填充`() {
        assertNull(subject.quoteWithUnknownUpdateTime().updatedAt)
    }

    @Test
    fun `Mock 来源不得支持实时`() {
        val quote = subject.mockQuote()

        assertEquals("MOCK", quote.status)
        assertFalse(quote.supportsRealtime)
        assertEquals("STATIC_SAMPLE", quote.sourceType)
    }
}

interface StockQuoteContractSubject {
    fun successQuote(): StockQuoteContractView
    fun quoteWithMissingValues(): StockQuoteContractView
    fun quoteWithUnknownUpdateTime(): StockQuoteContractView
    fun mockQuote(): StockQuoteContractView
}

data class StockQuoteContractView(
    val symbol: String,
    val name: String,
    val lastPrice: Double?,
    val changePercent: Double?,
    val volume: Long?,
    val turnoverAmount: Double?,
    val turnoverRate: Double?,
    val updatedAt: Instant?,
    val status: String,
    val providerId: String,
    val sourceType: String,
    val supportsRealtime: Boolean
)
