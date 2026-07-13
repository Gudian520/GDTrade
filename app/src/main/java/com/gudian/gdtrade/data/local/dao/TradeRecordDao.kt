package com.gudian.gdtrade.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gudian.gdtrade.data.local.entity.TradeRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeRecordDao {
    @Query("SELECT * FROM trade_records ORDER BY id DESC")
    fun observeAll(): Flow<List<TradeRecordEntity>>

    @Query("SELECT COUNT(*) FROM trade_records")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: TradeRecordEntity)

    @Insert
    suspend fun insertAll(entities: List<TradeRecordEntity>)

    @Query("DELETE FROM trade_records WHERE record_key = :recordKey")
    suspend fun deleteByRecordKey(recordKey: String)

    @Query("DELETE FROM trade_records")
    suspend fun deleteAll()
}
