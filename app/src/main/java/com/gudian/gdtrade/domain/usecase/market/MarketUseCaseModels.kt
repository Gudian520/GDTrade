package com.gudian.gdtrade.domain.usecase.market

import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.market.DataCompleteness
import com.gudian.gdtrade.domain.model.market.MarketDataState
import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.QuoteSnapshot
import com.gudian.gdtrade.domain.model.market.StockQuote

/** 持仓或观察池在请求行情前的显式范围状态。 */
enum class QuoteCollectionState {
    EMPTY,
    ACTIVE,
    INVALID_SYMBOLS_ONLY
}

/** 行情可支持的研究用途，不代表可执行买卖结论。 */
enum class MarketDataUsage {
    RESEARCH_INPUT,
    OBSERVATION_ONLY,
    TEST_ONLY,
    UNAVAILABLE
}

enum class MarketDataQualityReason {
    EMPTY_SCOPE,
    INVALID_SYMBOL,
    LOADING,
    ERROR,
    DELAYED,
    MOCK,
    NO_DATA,
    INCOMPLETE,
    MISSING_SYMBOLS,
    NON_REALTIME_SOURCE
}

data class MarketDataQualityAssessment(
    val usage: MarketDataUsage,
    val reasons: Set<MarketDataQualityReason>
) {
    /** 只表示能否进入后续研究候选判断，最终结果仍必须经过 RiskEngine。 */
    val supportsResearchConclusion: Boolean
        get() = usage == MarketDataUsage.RESEARCH_INPUT
}

data class PositionQuoteItem(
    val position: Position,
    val normalizedSymbol: String?,
    val quote: StockQuote?
)

data class PortfolioQuotesResult(
    val collectionState: QuoteCollectionState,
    val items: List<PositionQuoteItem>,
    val orderedSymbols: List<String>,
    val invalidSymbols: List<String>,
    val marketDataState: MarketDataState<QuoteSnapshot>?,
    val quality: MarketDataQualityAssessment
) {
    val marketDataStatus: MarketDataStatus?
        get() = marketDataState?.status

    val missingSymbols: Set<String>
        get() = marketDataState?.data?.missingSymbols.orEmpty()

    val completeness: DataCompleteness
        get() = marketDataState?.data?.completeness ?: DataCompleteness.EMPTY
}

data class WatchlistQuoteItem(
    val candidate: StockCandidate,
    val normalizedSymbol: String?,
    val quote: StockQuote?
)

data class WatchlistQuotesResult(
    val collectionState: QuoteCollectionState,
    val items: List<WatchlistQuoteItem>,
    val orderedSymbols: List<String>,
    val invalidSymbols: List<String>,
    val marketDataState: MarketDataState<QuoteSnapshot>?,
    val quality: MarketDataQualityAssessment
) {
    val marketDataStatus: MarketDataStatus?
        get() = marketDataState?.status

    val missingSymbols: Set<String>
        get() = marketDataState?.data?.missingSymbols.orEmpty()

    val completeness: DataCompleteness
        get() = marketDataState?.data?.completeness ?: DataCompleteness.EMPTY
}

data class StockDetailResult(
    val requestedSymbol: String,
    val normalizedSymbol: String?,
    val marketDataState: MarketDataState<StockQuote>,
    val quality: MarketDataQualityAssessment
) {
    val quote: StockQuote?
        get() = marketDataState.data
}

enum class MarketOverviewCapability {
    FULL_MARKET_COVERAGE,
    SECTOR_DATA,
    CAPITAL_FLOW_DATA
}

sealed interface MarketOverviewResult {
    data class InsufficientData(
        val missingCapabilities: Set<MarketOverviewCapability>,
        val reason: String
    ) : MarketOverviewResult
}
