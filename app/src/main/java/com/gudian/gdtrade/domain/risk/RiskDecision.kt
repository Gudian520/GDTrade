package com.gudian.gdtrade.domain.risk

import com.gudian.gdtrade.domain.model.SignalStatus

data class RiskDecision(
    val allowed: Boolean,
    val status: SignalStatus,
    val reason: String
)
