package com.gudian.gdtrade.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gudian.gdtrade.data.local.entity.StockCandidateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockCandidateDao {
    @Query("SELECT * FROM stock_candidates ORDER BY display_order ASC")
    fun observeAll(): Flow<List<StockCandidateEntity>>

    @Query("SELECT COUNT(*) FROM stock_candidates")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(display_order), -1) FROM stock_candidates")
    suspend fun maxDisplayOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StockCandidateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<StockCandidateEntity>)

    @Query("DELETE FROM stock_candidates WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)

    @Query("DELETE FROM stock_candidates")
    suspend fun deleteAll()
}
