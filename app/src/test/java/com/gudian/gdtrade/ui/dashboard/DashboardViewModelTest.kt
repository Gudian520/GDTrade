package com.gudian.gdtrade.ui.dashboard

import com.gudian.gdtrade.data.repository.MarketRepository
import com.gudian.gdtrade.data.repository.PortfolioRepository
import com.gudian.gdtrade.domain.model.AccountGoal
import com.gudian.gdtrade.domain.model.MarketQuote
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.SignalStatus
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord
import com.gudian.gdtrade.domain.model.TradeSide
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun dashboardCombinesRepositoryStateAndAppliesRiskDecision() = runTest {
        val position = Position("002185", "华天科技", 200, "状态组合测试")
        val quote = MarketQuote(
            symbol = "002185",
            name = "华天科技",
            lastPrice = 24.62,
            changePercent = 1.2,
            sourceLabel = "测试行情，非实时行情",
            isRealtime = false
        )
        val researchCandidate = StockCandidate(
            symbol = "002185",
            name = "华天科技",
            theme = "半导体",
            reason = "研究型买点",
            signalStatus = SignalStatus.ResearchBuyPoint,
            riskDeniedBuy = false
        )
        val deniedCandidate = StockCandidate(
            symbol = "000725",
            name = "京东方A",
            theme = "面板",
            reason = "等待回调",
            signalStatus = SignalStatus.WaitPullback,
            riskDeniedBuy = false
        )
        val record = TradeRecord(
            tradeDate = LocalDate.of(2026, 7, 13),
            symbol = "002185",
            name = "华天科技",
            side = TradeSide.Sell,
            price = 24.62,
            quantity = 100,
            note = "状态组合测试"
        )
        val portfolioRepository = FakePortfolioRepository(
            initialPositions = listOf(position),
            initialRecords = listOf(record)
        )
        val marketRepository = FakeMarketRepository(
            initialQuotes = listOf(quote),
            initialCandidates = listOf(researchCandidate, deniedCandidate)
        )

        val viewModel = DashboardViewModel(
            portfolioRepository = portfolioRepository,
            marketRepository = marketRepository
        )
        advanceUntilIdle()

        val initialState = viewModel.uiState.value
        assertEquals(listOf(position), initialState.positions)
        assertEquals(listOf(quote), initialState.quotes)
        assertEquals(listOf(record), initialState.tradeRecords)
        assertFalse(
            initialState.candidates.first { it.symbol == researchCandidate.symbol }.riskDeniedBuy
        )
        assertTrue(
            initialState.candidates.first { it.symbol == deniedCandidate.symbol }.riskDeniedBuy
        )

        val updatedPosition = position.copy(quantity = 300)
        portfolioRepository.positions.value = listOf(updatedPosition)
        advanceUntilIdle()

        assertEquals(300, viewModel.uiState.value.positions.single().quantity)

        viewModel.refreshMarketQuotes()
        advanceUntilIdle()

        assertEquals(1, marketRepository.refreshCount)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakePortfolioRepository(
    initialPositions: List<Position>,
    initialRecords: List<TradeRecord>
) : PortfolioRepository {
    val positions = MutableStateFlow(initialPositions)
    private val records = MutableStateFlow(initialRecords)

    override fun observeAccountGoals(): Flow<List<AccountGoal>> {
        return MutableStateFlow(emptyList())
    }

    override fun observePositions(): Flow<List<Position>> = positions

    override fun observeTradeRecords(): Flow<List<TradeRecord>> = records

    override suspend fun addPosition(position: Position) {
        positions.value = positions.value + position
    }

    override suspend fun removePosition(symbol: String) {
        positions.value = positions.value.filterNot { it.symbol == symbol }
    }

    override suspend fun addTradeRecord(record: TradeRecord) {
        records.value = listOf(record) + records.value
    }

    override suspend fun removeTradeRecord(recordKey: String) = Unit

    override suspend fun resetPortfolioData() = Unit
}

private class FakeMarketRepository(
    initialQuotes: List<MarketQuote>,
    initialCandidates: List<StockCandidate>
) : MarketRepository {
    private val quotes = MutableStateFlow(initialQuotes)
    private val candidates = MutableStateFlow(initialCandidates)
    var refreshCount: Int = 0
        private set

    override fun observeQuotes(symbols: List<String>): Flow<List<MarketQuote>> = quotes

    override fun observeCandidates(): Flow<List<StockCandidate>> = candidates

    override suspend fun addCandidate(candidate: StockCandidate) {
        candidates.value = candidates.value + candidate
    }

    override suspend fun removeCandidate(symbol: String) {
        candidates.value = candidates.value.filterNot { it.symbol == symbol }
    }

    override suspend fun refreshMarketQuotes() {
        refreshCount += 1
    }

    override suspend fun resetMarketData() = Unit
}
