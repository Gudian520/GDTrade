package com.gudian.gdtrade.market.contract

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证状态映射与交易信号边界的可复用契约。 */
abstract class MarketDataStatusContractTest {
    protected abstract val subject: MarketDataStatusContractSubject

    @Test
    fun `SUCCESS 仅在来源明确支持时才能标记实时`() {
        assertTrue(subject.isRealtime("SUCCESS", sourceSupportsRealtime = true))
        assertFalse(subject.isRealtime("SUCCESS", sourceSupportsRealtime = false))
    }

    @Test
    fun `LOADING ERROR DELAYED MOCK 均不能标记实时`() {
        listOf("LOADING", "ERROR", "DELAYED", "MOCK").forEach { status ->
            assertFalse(subject.isRealtime(status, sourceSupportsRealtime = true))
        }
    }

    @Test
    fun `ERROR 不能生成有效交易信号`() {
        assertFalse(subject.canGenerateTradingSignal("ERROR"))
    }

    @Test
    fun `DELAYED 和 MOCK 不能作为实时行情输入`() {
        assertFalse(subject.canUseAsRealtimeQuote("DELAYED"))
        assertFalse(subject.canUseAsRealtimeQuote("MOCK"))
    }
}

interface MarketDataStatusContractSubject {
    fun isRealtime(status: String, sourceSupportsRealtime: Boolean): Boolean
    fun canGenerateTradingSignal(status: String): Boolean
    fun canUseAsRealtimeQuote(status: String): Boolean
}
