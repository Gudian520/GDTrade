package com.gudian.gdtrade.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "positions")
data class PositionEntity(
    @PrimaryKey
    val symbol: String,
    val name: String,
    val quantity: Int,
    val note: String,
    @ColumnInfo(name = "display_order")
    val displayOrder: Int
)
