package com.gudian.gdtrade.data.repository

import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.MarketSourceInfo
import com.gudian.gdtrade.domain.model.market.MarketSourceType
import com.gudian.gdtrade.domain.model.market.StockQuote
import java.time.Clock

interface FallbackMarketDataSource {
    suspend fun getQuotes(symbols: Set<String>): Map<String, StockQuote>
}

/** 网络失败或部分缺失时使用的明确 Mock 回退源。 */
class StaticFallbackMarketDataSource(
    private val clock: Clock = Clock.systemUTC(),
    private val samples: Map<String, FallbackQuoteSample> = DEFAULT_SAMPLES
) : FallbackMarketDataSource {
    override suspend fun getQuotes(symbols: Set<String>): Map<String, StockQuote> {
        val receivedAt = clock.instant()
        return symbols.associateWithTo(linkedMapOf()) { symbol ->
            val sample = samples[symbol]
            StockQuote(
                symbol = symbol,
                name = sample?.name ?: symbol,
                lastPrice = sample?.lastPrice,
                changePercent = sample?.changePercent,
                volume = null,
                turnoverAmount = null,
                turnoverRate = null,
                updatedAt = null,
                dataStatus = MarketDataStatus.MOCK,
                source = MarketSourceInfo(
                    providerId = FALLBACK_PROVIDER_ID,
                    sourceType = MarketSourceType.FALLBACK,
                    supportsRealtime = false,
                    latency = null,
                    description = "静态回退样例，非实时行情",
                    receivedAt = receivedAt
                )
            )
        }
    }

    data class FallbackQuoteSample(
        val name: String,
        val lastPrice: Double?,
        val changePercent: Double?
    )

    companion object {
        const val FALLBACK_PROVIDER_ID = "FALLBACK"

        private val DEFAULT_SAMPLES = mapOf(
            "002185" to FallbackQuoteSample("华天科技", 24.62, null),
            "515070" to FallbackQuoteSample("华夏中证人工智能主题ETF", null, null),
            "000725" to FallbackQuoteSample("京东方A", 6.92, null)
        )
    }
}
