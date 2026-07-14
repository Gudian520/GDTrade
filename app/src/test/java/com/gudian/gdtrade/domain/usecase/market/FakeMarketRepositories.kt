package com.gudian.gdtrade.domain.usecase.market

import com.gudian.gdtrade.data.repository.MarketRepository
import com.gudian.gdtrade.data.repository.PortfolioRepository
import com.gudian.gdtrade.domain.model.AccountGoal
import com.gudian.gdtrade.domain.model.MarketQuote
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord
import com.gudian.gdtrade.domain.model.market.MarketDataState
import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.QuoteRequest
import com.gudian.gdtrade.domain.model.market.QuoteSnapshot
import com.gudian.gdtrade.domain.model.market.RefreshMarketResult
import com.gudian.gdtrade.domain.model.market.SingleQuoteRequest
import com.gudian.gdtrade.domain.model.market.StockQuote
import com.gudian.gdtrade.domain.repository.MarketDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf

internal class FakePortfolioRepository(
    positions: List<Position> = emptyList()
) : PortfolioRepository {
    val positions = MutableStateFlow(positions)

    override fun observeAccountGoals(): Flow<List<AccountGoal>> = flowOf(emptyList())

    override fun observePositions(): Flow<List<Position>> = positions

    override fun observeTradeRecords(): Flow<List<TradeRecord>> = flowOf(emptyList())

    override suspend fun addPosition(position: Position) = Unit

    override suspend fun removePosition(symbol: String) = Unit

    override suspend fun addTradeRecord(record: TradeRecord) = Unit

    override suspend fun removeTradeRecord(recordKey: String) = Unit

    override suspend fun resetPortfolioData() = Unit
}

internal class FakeMarketRepository(
    candidates: List<StockCandidate> = emptyList()
) : MarketRepository {
    val candidates = MutableStateFlow(candidates)

    override fun observeQuotes(symbols: List<String>): Flow<List<MarketQuote>> = flowOf(emptyList())

    override fun observeCandidates(): Flow<List<StockCandidate>> = candidates

    override suspend fun addCandidate(candidate: StockCandidate) = Unit

    override suspend fun removeCandidate(symbol: String) = Unit

    override suspend fun refreshMarketQuotes() = Unit

    override suspend fun resetMarketData() = Unit
}

internal class FakeMarketDataRepository : MarketDataRepository {
    val batchRequests = mutableListOf<QuoteRequest>()
    val singleRequests = mutableListOf<SingleQuoteRequest>()
    var batchStates: List<MarketDataState<QuoteSnapshot>> = emptyList()
    var singleStates: List<MarketDataState<StockQuote>> = emptyList()

    override fun observeQuote(request: SingleQuoteRequest): Flow<MarketDataState<StockQuote>> {
        singleRequests += request
        check(singleStates.isNotEmpty()) { "Fake 单股行情状态尚未配置" }
        return singleStates.asFlow()
    }

    override fun observeQuotes(request: QuoteRequest): Flow<MarketDataState<QuoteSnapshot>> {
        batchRequests += request
        check(batchStates.isNotEmpty()) { "Fake 批量行情状态尚未配置" }
        return batchStates.asFlow()
    }

    override suspend fun refreshQuotes(request: QuoteRequest): RefreshMarketResult {
        return RefreshMarketResult(
            requestedSymbols = request.symbols,
            refreshedSymbols = request.symbols,
            failedSymbols = emptySet(),
            status = MarketDataStatus.SUCCESS
        )
    }
}
