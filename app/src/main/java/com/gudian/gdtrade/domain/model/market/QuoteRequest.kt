package com.gudian.gdtrade.domain.model.market

import java.time.Duration

data class QuoteRequest(
    val symbols: Set<String>,
    val policy: FetchPolicy,
    val maxAge: Duration,
    val reason: QuoteRequestReason
) {
    init {
        require(symbols.isNotEmpty()) { "批量行情请求不能为空" }
        requireMarketSymbols(symbols)
        require(!maxAge.isNegative) { "maxAge 不能为负数" }
    }
}