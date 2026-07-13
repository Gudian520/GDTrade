package com.gudian.gdtrade.domain.model

data class StockCandidate(
    val symbol: String,
    val name: String,
    val theme: String,
    val reason: String,
    val signalStatus: SignalStatus,
    val riskDeniedBuy: Boolean
)
