package com.gudian.gdtrade.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_candidates")
data class StockCandidateEntity(
    @PrimaryKey
    val symbol: String,
    val name: String,
    val theme: String,
    val reason: String,
    @ColumnInfo(name = "signal_status")
    val signalStatus: String,
    @ColumnInfo(name = "risk_denied_buy")
    val riskDeniedBuy: Boolean,
    @ColumnInfo(name = "display_order")
    val displayOrder: Int
)
