package com.gudian.gdtrade.domain.model

import java.time.LocalDate

enum class TradeSide(val displayName: String) {
    Buy("买入"),
    Sell("卖出")
}

data class TradeRecord(
    val tradeDate: LocalDate,
    val symbol: String,
    val name: String,
    val side: TradeSide,
    val price: Double,
    val quantity: Int,
    val note: String
)
