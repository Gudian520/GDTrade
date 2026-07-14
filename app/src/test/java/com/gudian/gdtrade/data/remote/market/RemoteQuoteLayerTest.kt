package com.gudian.gdtrade.data.remote.market

import com.gudian.gdtrade.data.remote.market.dto.RemoteAmountUnit
import com.gudian.gdtrade.data.remote.market.dto.RemotePercentUnit
import com.gudian.gdtrade.data.remote.market.dto.RemotePriceUnit
import com.gudian.gdtrade.data.remote.market.dto.RemoteQuoteDTO
import com.gudian.gdtrade.data.remote.market.dto.RemoteQuoteSourceKind
import com.gudian.gdtrade.data.remote.market.dto.RemoteQuoteTimeFormat
import com.gudian.gdtrade.data.remote.market.dto.RemoteVolumeUnit
import com.gudian.gdtrade.data.remote.market.mapper.QuoteMapper
import com.gudian.gdtrade.data.remote.market.mapper.RemoteQuoteMappingResult
import com.gudian.gdtrade.data.remote.market.parser.QuoteParser
import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.MarketSourceType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteQuoteLayerTest {
    private val mapper = QuoteMapper()
    private val parser = QuoteParser()
    private val receivedAt = Instant.parse("2026-07-14T02:31:00Z")

    @Test
    fun `正常 DTO 转换必须完成单位时间状态和来源归一化`() {
        val result = mapper.map(
            baseDto().copy(
                lastPrice = "2462",
                lastPriceUnit = RemotePriceUnit.FEN,
                changePercent = "0.012",
                changePercentUnit = RemotePercentUnit.RATIO,
                volume = "356800",
                volumeUnit = RemoteVolumeUnit.LOT_100,
                turnoverAmount = "87640",
                turnoverAmountUnit = RemoteAmountUnit.TEN_THOUSAND_YUAN,
                turnoverRate = "2.18%",
                quoteTime = "2026-07-14T02:30:00Z",
                quoteTimeFormat = RemoteQuoteTimeFormat.ISO_INSTANT,
                supportsRealtime = true,
                declaredLatencySeconds = 2
            ),
            receivedAt
        )

        val quote = (result as RemoteQuoteMappingResult.Success).quote
        assertEquals("002185", quote.symbol)
        assertEquals("华天科技", quote.name)
        assertEquals(24.62, quote.lastPrice!!, 0.000001)
        assertEquals(1.2, quote.changePercent!!, 0.000001)
        assertEquals(35_680_000L, quote.volume)
        assertEquals(876_400_000.0, quote.turnoverAmount!!, 0.001)
        assertEquals(2.18, quote.turnoverRate!!, 0.000001)
        assertEquals(Instant.parse("2026-07-14T02:30:00Z"), quote.updatedAt)
        assertEquals(MarketDataStatus.SUCCESS, quote.dataStatus)
        assertEquals(MarketSourceType.REMOTE, quote.source.sourceType)
        assertTrue(quote.source.supportsRealtime)
        assertEquals(2L, quote.source.latency?.seconds)
        assertEquals(receivedAt, quote.source.receivedAt)
    }

    @Test
    fun `字段缺失必须保持 null 而不是零`() {
        val result = mapper.map(
            baseDto().copy(
                lastPrice = null,
                changePercent = " ",
                volume = null,
                turnoverAmount = "",
                turnoverRate = null
            ),
            receivedAt
        )

        val quote = (result as RemoteQuoteMappingResult.Success).quote
        assertNull(quote.lastPrice)
        assertNull(quote.changePercent)
        assertNull(quote.volume)
        assertNull(quote.turnoverAmount)
        assertNull(quote.turnoverRate)
    }

    @Test
    fun `供应商时间缺失必须保持 null 且不能伪装成功`() {
        val result = mapper.map(
            baseDto().copy(
                quoteTime = null,
                supportsRealtime = true
            ),
            receivedAt
        )

        val quote = (result as RemoteQuoteMappingResult.Success).quote
        assertNull(quote.updatedAt)
        assertEquals(MarketDataStatus.DELAYED, quote.dataStatus)
        assertEquals(receivedAt, quote.source.receivedAt)
    }

    @Test
    fun `错误响应格式必须返回结构化 ERROR`() {
        val result = parser.parseTencent("这不是有效的腾讯行情响应")

        assertTrue(result.quotes.isEmpty())
        assertEquals(1, result.errors.size)
        assertEquals(MarketDataStatus.ERROR, result.errors.single().dataStatus)
        assertEquals("REMOTE_MALFORMED_RESPONSE", result.errors.single().code)
    }

    @Test
    fun `批量响应部分格式错误时必须保留成功 DTO 和错误`() {
        val validRecord = tencentRecord()

        val result = parser.parseTencent("$validRecord;无效记录;")

        assertEquals(1, result.quotes.size)
        assertEquals("002185", result.quotes.single().providerSymbol)
        assertEquals(1, result.errors.size)
        assertEquals(MarketDataStatus.ERROR, result.errors.single().dataStatus)
    }

    @Test
    fun `非法数值必须返回结构化 ERROR 而不是零`() {
        val result = mapper.map(baseDto().copy(lastPrice = "--"), receivedAt)

        val failure = result as RemoteQuoteMappingResult.Failure
        assertEquals(MarketDataStatus.ERROR, failure.error.dataStatus)
        assertEquals("REMOTE_INVALID_FIELD", failure.error.code)
        assertTrue(failure.error.message.contains("lastPrice"))
    }

    @Test
    fun `静态样例和 fallback 必须强制为安全 Mock`() {
        listOf(
            RemoteQuoteSourceKind.STATIC_SAMPLE to MarketSourceType.STATIC_SAMPLE,
            RemoteQuoteSourceKind.FALLBACK to MarketSourceType.FALLBACK
        ).forEach { (sourceKind, expectedSourceType) ->
            val result = mapper.map(
                baseDto().copy(
                    sourceKind = sourceKind,
                    supportsRealtime = true
                ),
                receivedAt
            )

            val quote = (result as RemoteQuoteMappingResult.Success).quote
            assertEquals(MarketDataStatus.MOCK, quote.dataStatus)
            assertEquals(expectedSourceType, quote.source.sourceType)
            assertFalse(quote.source.supportsRealtime)
            assertTrue(quote.source.providerId in setOf("STATIC_SAMPLE", "FALLBACK"))
        }
    }

    @Test
    fun `腾讯 Parser 必须隔离供应商字段并映射为 DELAYED`() {
        val body = "${tencentRecord()};"

        val parsed = parser.parseTencent(body)
        assertTrue(parsed.errors.isEmpty())
        assertEquals("24.62", parsed.quotes.single().vendorFields["field_3"])

        val result = mapper.map(parsed.quotes.single(), receivedAt)
        val quote = (result as RemoteQuoteMappingResult.Success).quote
        assertEquals(MarketDataStatus.DELAYED, quote.dataStatus)
        assertFalse(quote.source.supportsRealtime)
        assertEquals(35_680_000L, quote.volume)
        assertEquals(876_400_000.0, quote.turnoverAmount!!, 0.001)
        assertEquals(Instant.parse("2026-07-14T02:30:00Z"), quote.updatedAt)
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

    private fun baseDto(): RemoteQuoteDTO {
        return RemoteQuoteDTO(
            providerId = "VERIFIED_REMOTE",
            providerSymbol = "sz002185",
            name = "华天科技",
            lastPrice = "24.62",
            changePercent = "1.20",
            volume = "35680000",
            turnoverAmount = "876400000",
            turnoverRate = "2.18",
            quoteTime = "2026-07-14T02:30:00Z",
            sourceDescription = "已验证远程测试源"
        )
    }
}
