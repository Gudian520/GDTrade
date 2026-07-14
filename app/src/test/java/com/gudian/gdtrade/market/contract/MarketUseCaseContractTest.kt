package com.gudian.gdtrade.market.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 未来行情 UseCase 的状态传递、空请求与风险边界契约。 */
abstract class MarketUseCaseContractTest {
    protected abstract val driver: MarketUseCaseContractDriver

    @Test
    fun `空持仓不发起无意义的行情请求`() {
        val result = driver.execute(UseCaseScenario.EMPTY_SYMBOLS)

        assertEquals(0, result.repositoryRequestCount)
        assertTrue(result.outputSymbols.isEmpty())
    }

    @Test
    fun `延迟和 Mock 状态必须原样传递到输出`() {
        val delayed = driver.execute(UseCaseScenario.DELAYED_INPUT)
        val mock = driver.execute(UseCaseScenario.MOCK_INPUT)

        assertEquals("DELAYED", delayed.outputStatus)
        assertEquals("MOCK", mock.outputStatus)
        assertFalse(delayed.canGenerateTradingSignal)
        assertFalse(mock.canGenerateTradingSignal)
    }

    @Test
    fun `ERROR 输入不得变换为有效交易信号`() {
        val result = driver.execute(UseCaseScenario.ERROR_INPUT)

        assertEquals("ERROR", result.outputStatus)
        assertFalse(result.canGenerateTradingSignal)
    }

    @Test
    fun `风险否决不得被行情研究信号覆盖`() {
        val result = driver.execute(UseCaseScenario.RISK_DENIED_RESEARCH_SIGNAL)

        assertTrue(result.riskDenied)
        assertFalse(result.canGenerateTradingSignal)
    }
}

interface MarketUseCaseContractDriver {
    fun execute(scenario: UseCaseScenario): UseCaseContractObservation
}

enum class UseCaseScenario {
    EMPTY_SYMBOLS,
    DELAYED_INPUT,
    MOCK_INPUT,
    ERROR_INPUT,
    RISK_DENIED_RESEARCH_SIGNAL
}

data class UseCaseContractObservation(
    val repositoryRequestCount: Int,
    val outputSymbols: Set<String>,
    val outputStatus: String,
    val canGenerateTradingSignal: Boolean,
    val riskDenied: Boolean
)
