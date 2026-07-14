package com.gudian.gdtrade.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gudian.gdtrade.data.local.DefaultLocalData
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.SignalStatus
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord
import com.gudian.gdtrade.domain.model.TradeSide
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryRegressionTest {
    private lateinit var context: Context

    @Before
    fun prepareRuntimeRepository() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("gd_trade_local_data", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.deleteDatabase("gd_trade.db")
    }

    @Test
    fun portfolioAndMarketRepositoryContractsRemainUsable() = runBlocking {
        val runtimeRepository = LocalPreferenceRepository(context)
        val portfolioRepository: PortfolioRepository = runtimeRepository
        val marketRepository: MarketRepository = runtimeRepository

        assertEquals(4, portfolioRepository.observeAccountGoals().first().size)
        assertEquals(
            DefaultLocalData.positions.size,
            portfolioRepository.observePositions().first().size
        )
        assertEquals(
            DefaultLocalData.tradeRecords.size,
            portfolioRepository.observeTradeRecords().first().size
        )
        assertEquals(
            DefaultLocalData.candidates.size,
            marketRepository.observeCandidates().first().size
        )

        val newPosition = Position("600000", "浦发银行", 100, "Repository 回归测试")
        portfolioRepository.addPosition(newPosition)
        assertNotNull(
            portfolioRepository.observePositions().first()
                .firstOrNull { it.symbol == newPosition.symbol }
        )
        portfolioRepository.removePosition(newPosition.symbol)
        assertFalse(
            portfolioRepository.observePositions().first()
                .any { it.symbol == newPosition.symbol }
        )

        val newRecord = TradeRecord(
            tradeDate = LocalDate.of(2026, 7, 14),
            symbol = "600000",
            name = "浦发银行",
            side = TradeSide.Buy,
            price = 10.0,
            quantity = 100,
            note = "Repository 回归测试"
        )
        portfolioRepository.addTradeRecord(newRecord)
        assertTrue(
            portfolioRepository.observeTradeRecords().first()
                .any { it.note == newRecord.note }
        )
        portfolioRepository.removeTradeRecord(newRecord.localKey)
        assertFalse(
            portfolioRepository.observeTradeRecords().first()
                .any { it.note == newRecord.note }
        )

        val newCandidate = StockCandidate(
            symbol = "600000",
            name = "浦发银行",
            theme = "银行",
            reason = "Repository 回归测试",
            signalStatus = SignalStatus.WatchOnly,
            riskDeniedBuy = false
        )
        marketRepository.addCandidate(newCandidate)
        assertNotNull(
            marketRepository.observeCandidates().first()
                .firstOrNull { it.symbol == newCandidate.symbol }
        )
        marketRepository.removeCandidate(newCandidate.symbol)
        assertFalse(
            marketRepository.observeCandidates().first()
                .any { it.symbol == newCandidate.symbol }
        )

        marketRepository.refreshMarketQuotes()
        portfolioRepository.resetPortfolioData()
        marketRepository.resetMarketData()

        assertEquals(
            DefaultLocalData.positions.size,
            portfolioRepository.observePositions().first().size
        )
        assertEquals(
            DefaultLocalData.tradeRecords.size,
            portfolioRepository.observeTradeRecords().first().size
        )
        assertEquals(
            DefaultLocalData.candidates.size,
            marketRepository.observeCandidates().first().size
        )
    }

    @Test
    fun staticMarketRepositoryQuotesAreClearlyNonRealtime() = runBlocking {
        val repository: MarketRepository = StaticMarketRepository()

        val quotes = repository.observeQuotes(listOf("002185")).first()

        assertEquals(listOf("002185"), quotes.map { it.symbol })
        assertTrue(quotes.all { !it.isRealtime })
        assertTrue(quotes.all { it.sourceLabel.contains("非实时行情") })
    }
}
