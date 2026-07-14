package com.gudian.gdtrade.domain.usecase.market

import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.market.DataCompleteness
import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.QuoteRequestReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketUseCasesTest {
    @Test
    fun `空持仓输出明确空状态且不调用行情仓储`() = runTest {
        val marketDataRepository = FakeMarketDataRepository()
        val result = GetPortfolioQuotesUseCase(
            portfolioRepository = FakePortfolioRepository(),
            marketDataRepository = marketDataRepository
        )().first()

        assertEquals(QuoteCollectionState.EMPTY, result.collectionState)
        assertTrue(result.items.isEmpty())
        assertNull(result.marketDataState)
        assertEquals(DataCompleteness.EMPTY, result.completeness)
        assertEquals(MarketDataUsage.UNAVAILABLE, result.quality.usage)
        assertTrue(marketDataRepository.batchRequests.isEmpty())
    }

    @Test
    fun `空观察池输出明确空状态且不调用行情仓储`() = runTest {
        val marketDataRepository = FakeMarketDataRepository()
        val result = GetWatchlistQuotesUseCase(
            marketRepository = FakeMarketRepository(),
            marketDataRepository = marketDataRepository
        )().first()

        assertEquals(QuoteCollectionState.EMPTY, result.collectionState)
        assertTrue(result.items.isEmpty())
        assertNull(result.marketDataState)
        assertTrue(marketDataRepository.batchRequests.isEmpty())
    }

    @Test
    fun `持仓代码标准化去重且组合结果保持原持仓顺序`() = runTest {
        val positions = listOf(
            position(" sz.000001 ", "第一项"),
            position("600000", "第二项"),
            position("SZ000001", "第三项")
        )
        val marketDataRepository = FakeMarketDataRepository().apply {
            batchStates = listOf(
                MarketUseCaseFixtures.batchState(
                    status = MarketDataStatus.SUCCESS,
                    requestedSymbols = linkedSetOf("000001", "600000")
                )
            )
        }

        val result = GetPortfolioQuotesUseCase(
            portfolioRepository = FakePortfolioRepository(positions),
            marketDataRepository = marketDataRepository
        )().first()

        val request = marketDataRepository.batchRequests.single()
        assertEquals(listOf("000001", "600000"), request.symbols.toList())
        assertEquals(QuoteRequestReason.PORTFOLIO, request.reason)
        assertEquals(listOf("000001", "600000"), result.orderedSymbols)
        assertEquals(positions, result.items.map { it.position })
        assertEquals(listOf("000001", "600000", "000001"), result.items.map { it.normalizedSymbol })
        assertEquals(listOf("000001", "600000", "000001"), result.items.map { it.quote?.symbol })
    }

    @Test
    fun `持仓从空集合变化为有效集合后才发起显式行情请求`() = runTest {
        val portfolioRepository = FakePortfolioRepository()
        val marketDataRepository = FakeMarketDataRepository().apply {
            batchStates = listOf(MarketUseCaseFixtures.batchState(MarketDataStatus.SUCCESS))
        }
        val results = mutableListOf<PortfolioQuotesResult>()
        val job = launch {
            GetPortfolioQuotesUseCase(portfolioRepository, marketDataRepository)()
                .take(2)
                .toList(results)
        }

        runCurrent()
        assertEquals(QuoteCollectionState.EMPTY, results.single().collectionState)
        assertTrue(marketDataRepository.batchRequests.isEmpty())

        portfolioRepository.positions.value = listOf(position("000001", "新增持仓"))
        job.join()

        assertEquals(QuoteCollectionState.ACTIVE, results.last().collectionState)
        assertEquals(setOf("000001"), marketDataRepository.batchRequests.single().symbols)
    }

    @Test
    fun `观察池代码标准化去重且组合结果保持原观察池顺序`() = runTest {
        val candidates = listOf(
            MarketUseCaseFixtures.candidate("SH600000"),
            MarketUseCaseFixtures.candidate(" 000001 "),
            MarketUseCaseFixtures.candidate("sh.600000")
        )
        val marketDataRepository = FakeMarketDataRepository().apply {
            batchStates = listOf(
                MarketUseCaseFixtures.batchState(
                    status = MarketDataStatus.SUCCESS,
                    requestedSymbols = linkedSetOf("600000", "000001")
                )
            )
        }

        val result = GetWatchlistQuotesUseCase(
            marketRepository = FakeMarketRepository(candidates),
            marketDataRepository = marketDataRepository
        )().first()

        val request = marketDataRepository.batchRequests.single()
        assertEquals(listOf("600000", "000001"), request.symbols.toList())
        assertEquals(QuoteRequestReason.WATCHLIST, request.reason)
        assertEquals(candidates, result.items.map { it.candidate })
        assertEquals(listOf("600000", "000001", "600000"), result.items.map { it.quote?.symbol })
    }

    @Test
    fun `股票详情完整保留五种行情状态和仓储错误`() = runTest {
        val states = MarketDataStatus.entries.map(MarketUseCaseFixtures::singleState)
        val marketDataRepository = FakeMarketDataRepository().apply {
            singleStates = states
        }

        val results = GetStockDetailUseCase(marketDataRepository)(" sz.000001 ").toList()

        assertEquals(listOf("000001"), marketDataRepository.singleRequests.map { it.symbol })
        assertEquals(QuoteRequestReason.STOCK_DETAIL, marketDataRepository.singleRequests.single().reason)
        assertEquals(states, results.map { it.marketDataState })
        assertNotNull(results.single { it.marketDataState.status == MarketDataStatus.ERROR }
            .marketDataState.error)
    }

    @Test
    fun `批量行情不丢失缺失代码完整度错误与逐只来源`() = runTest {
        val requested = linkedSetOf("000001", "600000")
        val partialState = MarketUseCaseFixtures.batchState(
            status = MarketDataStatus.DELAYED,
            requestedSymbols = requested,
            returnedSymbols = setOf("000001")
        )
        val marketDataRepository = FakeMarketDataRepository().apply {
            batchStates = listOf(partialState)
        }

        val result = GetPortfolioQuotesUseCase(
            portfolioRepository = FakePortfolioRepository(
                listOf(position("000001", "第一项"), position("600000", "第二项"))
            ),
            marketDataRepository = marketDataRepository
        )().first()

        assertEquals(partialState, result.marketDataState)
        assertEquals(setOf("600000"), result.missingSymbols)
        assertEquals(DataCompleteness.PARTIAL, result.completeness)
        assertNotNull(result.marketDataState?.error)
        assertEquals(MarketDataStatus.DELAYED, result.items.first().quote?.dataStatus)
        assertEquals("TEST_REMOTE", result.items.first().quote?.source?.providerId)
        assertNull(result.items.last().quote)
    }

    @Test
    fun `加载错误延迟和 Mock 的研究用途分级不会被转换成普通成功`() = runTest {
        val statuses = listOf(
            MarketDataStatus.LOADING,
            MarketDataStatus.ERROR,
            MarketDataStatus.DELAYED,
            MarketDataStatus.MOCK
        )
        val marketDataRepository = FakeMarketDataRepository().apply {
            singleStates = statuses.map(MarketUseCaseFixtures::singleState)
        }

        val results = GetStockDetailUseCase(marketDataRepository)("000001").toList()

        assertEquals(statuses, results.map { it.marketDataState.status })
        assertEquals(
            listOf(
                MarketDataUsage.OBSERVATION_ONLY,
                MarketDataUsage.UNAVAILABLE,
                MarketDataUsage.OBSERVATION_ONLY,
                MarketDataUsage.TEST_ONLY
            ),
            results.map { it.quality.usage }
        )
        assertTrue(results.none { it.quality.supportsResearchConclusion })
    }

    @Test
    fun `非法详情代码返回领域错误且不调用行情仓储`() = runTest {
        val marketDataRepository = FakeMarketDataRepository()

        val result = GetStockDetailUseCase(marketDataRepository)("invalid").first()

        assertEquals(MarketDataStatus.ERROR, result.marketDataState.status)
        assertEquals("INVALID_STOCK_DETAIL_SYMBOL", result.marketDataState.error?.code)
        assertNull(result.normalizedSymbol)
        assertTrue(marketDataRepository.singleRequests.isEmpty())
    }

    @Test
    fun `仅含非法持仓代码时不调用行情仓储`() = runTest {
        val marketDataRepository = FakeMarketDataRepository()

        val result = GetPortfolioQuotesUseCase(
            portfolioRepository = FakePortfolioRepository(listOf(position("INVALID", "非法项"))),
            marketDataRepository = marketDataRepository
        )().first()

        assertEquals(QuoteCollectionState.INVALID_SYMBOLS_ONLY, result.collectionState)
        assertEquals(MarketDataStatus.ERROR, result.marketDataStatus)
        assertEquals(listOf("INVALID"), result.invalidSymbols)
        assertTrue(marketDataRepository.batchRequests.isEmpty())
    }

    @Test
    fun `市场概览在能力不足时明确返回不可用范围`() {
        val result = GetMarketOverviewUseCase()()

        assertTrue(result is MarketOverviewResult.InsufficientData)
        result as MarketOverviewResult.InsufficientData
        assertEquals(
            setOf(
                MarketOverviewCapability.FULL_MARKET_COVERAGE,
                MarketOverviewCapability.SECTOR_DATA,
                MarketOverviewCapability.CAPITAL_FLOW_DATA
            ),
            result.missingCapabilities
        )
        assertFalse(result.reason.contains("全市场上涨"))
        assertFalse(result.reason.contains("资金流入"))
    }

    private fun position(symbol: String, name: String): Position {
        return Position(symbol = symbol, name = name, quantity = 100, note = "固定测试持仓")
    }
}
