package com.gudian.gdtrade.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gudian.gdtrade.data.local.entity.PositionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PositionDao {
    @Query("SELECT * FROM positions ORDER BY display_order ASC")
    fun observeAll(): Flow<List<PositionEntity>>

    @Query("SELECT COUNT(*) FROM positions")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(display_order), -1) FROM positions")
    suspend fun maxDisplayOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PositionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PositionEntity>)

    @Query("DELETE FROM positions WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)

    @Query("DELETE FROM positions")
    suspend fun deleteAll()
}
