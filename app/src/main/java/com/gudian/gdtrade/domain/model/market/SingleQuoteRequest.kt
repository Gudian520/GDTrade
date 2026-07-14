package com.gudian.gdtrade.domain.model.market

import java.time.Duration

data class SingleQuoteRequest(
    val symbol: String,
    val policy: FetchPolicy,
    val maxAge: Duration,
    val reason: QuoteRequestReason = QuoteRequestReason.STOCK_DETAIL
) {
    init {
        requireMarketSymbol(symbol)
        require(!maxAge.isNegative) { "maxAge 不能为负数" }
    }
}