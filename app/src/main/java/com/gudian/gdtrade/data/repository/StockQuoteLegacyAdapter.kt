package com.gudian.gdtrade.data.repository

import com.gudian.gdtrade.domain.model.MarketQuote
import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.StockQuote
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 只允许从富行情模型单向映射到旧 MarketQuote。 */
class StockQuoteLegacyAdapter {
    fun toLegacy(
        quote: StockQuote,
        now: Instant,
        maxAge: Duration,
        requestStatus: MarketDataStatus = quote.dataStatus
    ): MarketQuote {
        val effectiveStatus = if (
            requestStatus == MarketDataStatus.ERROR || requestStatus == MarketDataStatus.LOADING
        ) {
            requestStatus
        } else {
            quote.dataStatus
        }
        val updatedAt = quote.updatedAt
        val isFresh = updatedAt != null &&
            !updatedAt.isAfter(now) &&
            Duration.between(updatedAt, now) <= maxAge
        val isRealtime = effectiveStatus == MarketDataStatus.SUCCESS &&
            quote.source.supportsRealtime &&
            isFresh

        return MarketQuote(
            symbol = quote.symbol,
            name = quote.name,
            lastPrice = quote.lastPrice,
            changePercent = quote.changePercent,
            sourceLabel = buildSourceLabel(quote, effectiveStatus, updatedAt),
            isRealtime = isRealtime
        )
    }

    private fun buildSourceLabel(
        quote: StockQuote,
        effectiveStatus: MarketDataStatus,
        updatedAt: Instant?
    ): String {
        val timeText = updatedAt?.let { instant ->
            "，时间 ${SOURCE_TIME_FORMATTER.format(instant.atZone(SHANGHAI_ZONE))}"
        }.orEmpty()
        val qualityText = when (effectiveStatus) {
            MarketDataStatus.SUCCESS -> if (quote.source.supportsRealtime) {
                ""
            } else {
                "，非实时行情"
            }
            MarketDataStatus.LOADING -> "，加载中，非实时行情"
            MarketDataStatus.ERROR -> "，行情错误，非实时行情"
            MarketDataStatus.DELAYED -> "，延迟行情，非实时行情"
            MarketDataStatus.MOCK -> "，模拟行情，非实时行情"
        }
        return "${quote.source.description}$timeText$qualityText"
    }

    private companion object {
        val SHANGHAI_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val SOURCE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
