package com.gudian.gdtrade.market.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Remote、fallback 与内存快照的可复用 Repository 组合契约。 */
abstract class MarketRepositoryContractTest {
    protected abstract val driver: MarketRepositoryContractDriver

    @Test
    fun `远程全部成功时不调用 fallback`() {
        val result = driver.execute(RepositoryScenario.REMOTE_SUCCESS)

        assertEquals("SUCCESS", result.batchStatus)
        assertEquals(1, result.remoteCallCount)
        assertEquals(0, result.fallbackCallCount)
        assertTrue(result.missingSymbols.isEmpty())
    }

    @Test
    fun `远程失败后的静态回退必须明确为 MOCK`() {
        val result = driver.execute(RepositoryScenario.REMOTE_ERROR_WITH_FALLBACK)

        assertEquals("MOCK", result.batchStatus)
        assertEquals(setOf("002185"), result.mockSymbols)
        assertFalse(result.realtimeSymbols.contains("002185"))
    }

    @Test
    fun `部分缺失时只对缺失代码调用 fallback`() {
        val result = driver.execute(RepositoryScenario.PARTIAL_REMOTE_SUCCESS)

        assertEquals(setOf("000725"), result.fallbackRequestedSymbols)
        assertTrue(result.quotesBySymbol.containsKey("002185"))
        assertTrue(result.quotesBySymbol.containsKey("000725"))
        assertEquals("MOCK", result.quotesBySymbol.getValue("000725"))
    }

    @Test
    fun `无可用数据时保留 ERROR 而不伪造成功`() {
        val result = driver.execute(RepositoryScenario.REMOTE_ERROR_WITHOUT_FALLBACK)

        assertEquals("ERROR", result.batchStatus)
        assertTrue(result.quotesBySymbol.isEmpty())
    }
}

interface MarketRepositoryContractDriver {
    fun execute(scenario: RepositoryScenario): RepositoryContractObservation
}

enum class RepositoryScenario {
    REMOTE_SUCCESS,
    REMOTE_ERROR_WITH_FALLBACK,
    PARTIAL_REMOTE_SUCCESS,
    REMOTE_ERROR_WITHOUT_FALLBACK
}

data class RepositoryContractObservation(
    val batchStatus: String,
    val remoteCallCount: Int,
    val fallbackCallCount: Int,
    val fallbackRequestedSymbols: Set<String>,
    val missingSymbols: Set<String>,
    val mockSymbols: Set<String>,
    val realtimeSymbols: Set<String>,
    val quotesBySymbol: Map<String, String>
)
