package com.gudian.gdtrade.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trade_records",
    indices = [Index(value = ["record_key"])]
)
data class TradeRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "record_key")
    val recordKey: String,
    @ColumnInfo(name = "trade_date")
    val tradeDate: String,
    val symbol: String,
    val name: String,
    val side: String,
    val price: Double,
    val quantity: Int,
    val note: String
)
