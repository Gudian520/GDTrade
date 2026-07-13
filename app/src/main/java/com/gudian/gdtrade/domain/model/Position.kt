package com.gudian.gdtrade.domain.model

data class Position(
    val symbol: String,
    val name: String,
    val quantity: Int,
    val note: String
)
