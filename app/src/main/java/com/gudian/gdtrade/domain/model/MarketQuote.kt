package com.gudian.gdtrade.domain.model

data class MarketQuote(
    val symbol: String,
    val name: String,
    val lastPrice: Double?,
    val changePercent: Double?,
    val sourceLabel: String,
    val isRealtime: Boolean
)
