package com.gudian.gdtrade.domain.model.market

import java.time.Instant

data class StockQuote(
    val symbol: String,
    val name: String,
    val lastPrice: Double?,
    val changePercent: Double?,
    val volume: Long?,
    val turnoverAmount: Double?,
    val turnoverRate: Double?,
    val updatedAt: Instant?,
    val dataStatus: MarketDataStatus,
    val source: MarketSourceInfo
) {
    init {
        requireMarketSymbol(symbol)
        require(name.isNotBlank()) { "股票名称不能为空" }
        require(lastPrice == null || lastPrice >= 0.0) { "最新价格不能为负数" }
        require(volume == null || volume >= 0L) { "成交量不能为负数" }
        require(turnoverAmount == null || turnoverAmount >= 0.0) { "成交额不能为负数" }
        require(dataStatus != MarketDataStatus.MOCK || !source.supportsRealtime) {
            "Mock 行情不能声明支持实时"
        }
        require(
            dataStatus != MarketDataStatus.MOCK ||
                source.sourceType == MarketSourceType.STATIC_SAMPLE ||
                source.sourceType == MarketSourceType.FALLBACK
        ) {
            "Mock 行情来源必须是静态样例或 fallback"
        }
        require(source.sourceType != MarketSourceType.STATIC_SAMPLE || dataStatus == MarketDataStatus.MOCK) {
            "静态样例必须标记为 Mock"
        }
        require(
            source.sourceType != MarketSourceType.FALLBACK ||
                dataStatus == MarketDataStatus.MOCK ||
                dataStatus == MarketDataStatus.ERROR
        ) {
            "Fallback 行情只能标记为 Mock 或 Error"
        }
    }
}