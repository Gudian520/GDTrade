package com.gudian.gdtrade.domain.usecase.market

import com.gudian.gdtrade.domain.model.SignalStatus
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.market.DataCompleteness
import com.gudian.gdtrade.domain.model.market.MarketDataError
import com.gudian.gdtrade.domain.model.market.MarketDataState
import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.MarketSourceInfo
import com.gudian.gdtrade.domain.model.market.MarketSourceType
import com.gudian.gdtrade.domain.model.market.QuoteSnapshot
import com.gudian.gdtrade.domain.model.market.StockQuote
import java.time.Duration
import java.time.Instant

internal object MarketUseCaseFixtures {
    val fixedTime: Instant = Instant.parse("2026-07-14T01:30:00Z")

    fun candidate(
        symbol: String,
        riskDeniedBuy: Boolean = false,
        signalStatus: SignalStatus = SignalStatus.HoldWatch
    ): StockCandidate {
        return StockCandidate(
            symbol = symbol,
            name = "测试股票$symbol",
            theme = "测试主题",
            reason = "固定测试输入",
            signalStatus = signalStatus,
            riskDeniedBuy = riskDeniedBuy
        )
    }

    fun quote(symbol: String, status: MarketDataStatus): StockQuote {
        val isMock = status == MarketDataStatus.MOCK
        val supportsRealtime = status == MarketDataStatus.SUCCESS
        return StockQuote(
            symbol = symbol,
            name = "测试股票$symbol",
            lastPrice = 10.25,
            changePercent = 1.20,
            volume = 1_000L,
            turnoverAmount = 10_250.0,
            turnoverRate = 0.50,
            updatedAt = fixedTime,
            dataStatus = status,
            source = MarketSourceInfo(
                providerId = if (isMock) "STATIC_SAMPLE" else "TEST_REMOTE",
                sourceType = if (isMock) MarketSourceType.STATIC_SAMPLE else MarketSourceType.REMOTE,
                supportsRealtime = supportsRealtime,
                latency = if (status == MarketDataStatus.DELAYED) Duration.ofMinutes(15) else null,
                description = if (isMock) "固定静态测试样例" else "固定远程测试来源",
                receivedAt = fixedTime
            )
        )
    }

    fun batchState(
        status: MarketDataStatus,
        requestedSymbols: Set<String> = linkedSetOf("000001"),
        returnedSymbols: Set<String> = requestedSymbols
    ): MarketDataState<QuoteSnapshot> {
        val error = if (status == MarketDataStatus.ERROR || returnedSymbols != requestedSymbols) {
            MarketDataError(
                code = "TEST_MARKET_ERROR",
                message = "固定行情错误",
                retryable = true,
                affectedSymbols = requestedSymbols - returnedSymbols,
                providerId = "TEST_REMOTE"
            )
        } else {
            null
        }
        if (status == MarketDataStatus.ERROR && returnedSymbols.isEmpty()) {
            return MarketDataState(status = status, data = null, error = error)
        }
        val quoteStatus = when (status) {
            MarketDataStatus.LOADING -> MarketDataStatus.SUCCESS
            else -> status
        }
        val quotes = returnedSymbols.associateWithTo(linkedMapOf()) { symbol ->
            quote(symbol, quoteStatus)
        }
        val missingSymbols = requestedSymbols - quotes.keys
        val completeness = when {
            quotes.isEmpty() -> DataCompleteness.EMPTY
            missingSymbols.isEmpty() -> DataCompleteness.COMPLETE
            else -> DataCompleteness.PARTIAL
        }
        return MarketDataState(
            status = status,
            data = QuoteSnapshot(
                requestedSymbols = requestedSymbols,
                quotes = quotes,
                missingSymbols = missingSymbols,
                completeness = completeness,
                generatedAt = fixedTime
            ),
            error = error
        )
    }

    fun singleState(status: MarketDataStatus): MarketDataState<StockQuote> {
        val error = if (status == MarketDataStatus.ERROR) {
            MarketDataError(
                code = "TEST_STOCK_DETAIL_ERROR",
                message = "固定详情错误",
                retryable = true,
                affectedSymbols = setOf("000001"),
                providerId = "TEST_REMOTE"
            )
        } else {
            null
        }
        val data = when (status) {
            MarketDataStatus.LOADING,
            MarketDataStatus.ERROR -> null
            else -> quote("000001", status)
        }
        return MarketDataState(status = status, data = data, error = error)
    }
}
