package com.gudian.gdtrade.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gudian.gdtrade.data.local.dao.PositionDao
import com.gudian.gdtrade.data.local.dao.StockCandidateDao
import com.gudian.gdtrade.data.local.dao.TradeRecordDao
import com.gudian.gdtrade.data.local.entity.PositionEntity
import com.gudian.gdtrade.data.local.entity.StockCandidateEntity
import com.gudian.gdtrade.data.local.entity.TradeRecordEntity

@Database(
    entities = [
        PositionEntity::class,
        TradeRecordEntity::class,
        StockCandidateEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class GdTradeDatabase : RoomDatabase() {
    abstract fun positionDao(): PositionDao

    abstract fun tradeRecordDao(): TradeRecordDao

    abstract fun stockCandidateDao(): StockCandidateDao

    companion object {
        private const val DATABASE_NAME = "gd_trade.db"

        @Volatile
        private var instance: GdTradeDatabase? = null

        fun getInstance(context: Context): GdTradeDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GdTradeDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
        }
    }
}
