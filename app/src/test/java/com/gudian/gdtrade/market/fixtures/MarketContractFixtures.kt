package com.gudian.gdtrade.market.fixtures

import java.time.Instant

enum class FixtureMarketDataStatus {
    SUCCESS,
    LOADING,
    ERROR,
    DELAYED,
    MOCK
}

enum class FixtureMarketSourceType {
    REMOTE,
    LOCAL_CACHE,
    STATIC_SAMPLE,
    FALLBACK
}

data class FixtureMarketSourceInfo(
    val providerId: String,
    val displayName: String,
    val sourceType: FixtureMarketSourceType,
    val supportsRealtime: Boolean,
    val declaredDelaySeconds: Long?,
    val receivedAt: Instant
)

data class FixtureStockQuote(
    val symbol: String,
    val name: String,
    val lastPrice: Double?,
    val changePercent: Double?,
    val volume: Long?,
    val turnoverAmount: Double?,
    val turnoverRate: Double?,
    val updatedAt: Instant?,
    val dataStatus: FixtureMarketDataStatus,
    val source: FixtureMarketSourceInfo,
    val canGenerateTradingSignal: Boolean
)

object MarketContractFixtures {
    private val receivedAt = Instant.parse("2026-07-14T02:31:00Z")

    val successQuote = FixtureStockQuote(
        symbol = "002185",
        name = "华天科技",
        lastPrice = 24.62,
        changePercent = 1.20,
        volume = 35_680_000,
        turnoverAmount = 876_400_000.0,
        turnoverRate = 2.18,
        updatedAt = Instant.parse("2026-07-14T02:30:00Z"),
        dataStatus = FixtureMarketDataStatus.SUCCESS,
        source = FixtureMarketSourceInfo(
            providerId = "VERIFIED_REMOTE",
            displayName = "已验证远程测试源",
            sourceType = FixtureMarketSourceType.REMOTE,
            supportsRealtime = true,
            declaredDelaySeconds = 0,
            receivedAt = receivedAt
        ),
        canGenerateTradingSignal = true
    )

    val delayedQuote = successQuote.copy(
        updatedAt = Instant.parse("2026-07-14T02:15:00Z"),
        dataStatus = FixtureMarketDataStatus.DELAYED,
        source = successQuote.source.copy(
            providerId = "DELAYED_PROVIDER",
            displayName = "延迟行情测试源",
            supportsRealtime = false,
            declaredDelaySeconds = 900
        ),
        canGenerateTradingSignal = false
    )

    val mockQuote = successQuote.copy(
        updatedAt = null,
        dataStatus = FixtureMarketDataStatus.MOCK,
        source = successQuote.source.copy(
            providerId = "STATIC_SAMPLE",
            displayName = "静态样例",
            sourceType = FixtureMarketSourceType.STATIC_SAMPLE,
            supportsRealtime = false,
            declaredDelaySeconds = null
        ),
        canGenerateTradingSignal = false
    )

    val errorQuote = successQuote.copy(
        lastPrice = null,
        changePercent = null,
        volume = null,
        turnoverAmount = null,
        turnoverRate = null,
        updatedAt = null,
        dataStatus = FixtureMarketDataStatus.ERROR,
        source = successQuote.source.copy(
            providerId = "REMOTE_ERROR",
            displayName = "远程行情错误",
            supportsRealtime = false,
            declaredDelaySeconds = null
        ),
        canGenerateTradingSignal = false
    )

    val loadingQuote = successQuote.copy(
        dataStatus = FixtureMarketDataStatus.LOADING,
        source = successQuote.source.copy(supportsRealtime = false),
        canGenerateTradingSignal = false
    )
}
