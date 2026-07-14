package com.gudian.gdtrade.domain.usecase.market

import com.gudian.gdtrade.domain.model.market.DataCompleteness
import com.gudian.gdtrade.domain.model.market.MarketDataState
import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.QuoteSnapshot
import com.gudian.gdtrade.domain.model.market.StockQuote

internal object MarketDataQualityInterpreter {
    fun emptyScope(): MarketDataQualityAssessment {
        return MarketDataQualityAssessment(
            usage = MarketDataUsage.UNAVAILABLE,
            reasons = setOf(MarketDataQualityReason.EMPTY_SCOPE)
        )
    }

    fun invalidSymbolsOnly(): MarketDataQualityAssessment {
        return MarketDataQualityAssessment(
            usage = MarketDataUsage.UNAVAILABLE,
            reasons = setOf(MarketDataQualityReason.INVALID_SYMBOL)
        )
    }

    fun assess(
        state: MarketDataState<QuoteSnapshot>,
        hasInvalidSymbols: Boolean
    ): MarketDataQualityAssessment {
        val reasons = linkedSetOf<MarketDataQualityReason>()
        addStatusReason(state.status, reasons)
        if (hasInvalidSymbols) reasons += MarketDataQualityReason.INVALID_SYMBOL

        val snapshot = state.data
        if (snapshot == null) {
            reasons += MarketDataQualityReason.NO_DATA
        } else {
            if (snapshot.completeness != DataCompleteness.COMPLETE) {
                reasons += MarketDataQualityReason.INCOMPLETE
            }
            if (snapshot.missingSymbols.isNotEmpty()) {
                reasons += MarketDataQualityReason.MISSING_SYMBOLS
            }
            addQuoteReasons(snapshot.quotes.values, reasons)
        }

        val usage = when {
            state.status == MarketDataStatus.ERROR -> MarketDataUsage.UNAVAILABLE
            state.status == MarketDataStatus.MOCK ||
                snapshot?.quotes?.values?.any { it.dataStatus == MarketDataStatus.MOCK } == true -> {
                MarketDataUsage.TEST_ONLY
            }
            state.status == MarketDataStatus.DELAYED -> MarketDataUsage.OBSERVATION_ONLY
            state.status == MarketDataStatus.LOADING -> MarketDataUsage.OBSERVATION_ONLY
            snapshot == null -> MarketDataUsage.UNAVAILABLE
            snapshot.quotes.values.any { it.dataStatus == MarketDataStatus.ERROR } -> {
                MarketDataUsage.UNAVAILABLE
            }
            snapshot.completeness != DataCompleteness.COMPLETE || hasInvalidSymbols -> {
                MarketDataUsage.OBSERVATION_ONLY
            }
            snapshot.quotes.values.any {
                it.dataStatus == MarketDataStatus.DELAYED ||
                    it.dataStatus == MarketDataStatus.LOADING
            } -> MarketDataUsage.OBSERVATION_ONLY
            else -> MarketDataUsage.RESEARCH_INPUT
        }
        return MarketDataQualityAssessment(usage = usage, reasons = reasons)
    }

    fun assess(state: MarketDataState<StockQuote>): MarketDataQualityAssessment {
        val reasons = linkedSetOf<MarketDataQualityReason>()
        addStatusReason(state.status, reasons)
        val quote = state.data
        if (quote == null) {
            reasons += MarketDataQualityReason.NO_DATA
        } else {
            addQuoteReasons(listOf(quote), reasons)
        }

        val usage = when {
            state.status == MarketDataStatus.ERROR -> MarketDataUsage.UNAVAILABLE
            state.status == MarketDataStatus.MOCK || quote?.dataStatus == MarketDataStatus.MOCK -> {
                MarketDataUsage.TEST_ONLY
            }
            state.status == MarketDataStatus.DELAYED -> MarketDataUsage.OBSERVATION_ONLY
            state.status == MarketDataStatus.LOADING -> MarketDataUsage.OBSERVATION_ONLY
            quote == null -> MarketDataUsage.UNAVAILABLE
            quote.dataStatus == MarketDataStatus.ERROR -> MarketDataUsage.UNAVAILABLE
            quote.dataStatus == MarketDataStatus.DELAYED ||
                quote.dataStatus == MarketDataStatus.LOADING -> MarketDataUsage.OBSERVATION_ONLY
            else -> MarketDataUsage.RESEARCH_INPUT
        }
        return MarketDataQualityAssessment(usage = usage, reasons = reasons)
    }

    private fun addStatusReason(
        status: MarketDataStatus,
        reasons: MutableSet<MarketDataQualityReason>
    ) {
        val reason = when (status) {
            MarketDataStatus.SUCCESS -> null
            MarketDataStatus.LOADING -> MarketDataQualityReason.LOADING
            MarketDataStatus.ERROR -> MarketDataQualityReason.ERROR
            MarketDataStatus.DELAYED -> MarketDataQualityReason.DELAYED
            MarketDataStatus.MOCK -> MarketDataQualityReason.MOCK
        }
        if (reason != null) reasons += reason
    }

    private fun addQuoteReasons(
        quotes: Collection<StockQuote>,
        reasons: MutableSet<MarketDataQualityReason>
    ) {
        quotes.forEach { quote ->
            addStatusReason(quote.dataStatus, reasons)
            if (!quote.source.supportsRealtime) {
                reasons += MarketDataQualityReason.NON_REALTIME_SOURCE
            }
        }
    }
}
