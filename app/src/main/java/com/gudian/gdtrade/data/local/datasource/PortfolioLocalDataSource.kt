package com.gudian.gdtrade.data.local.datasource

import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.TradeRecord
import kotlinx.coroutines.flow.Flow

interface PortfolioLocalDataSource {
    fun observePositions(): Flow<List<Position>>

    fun observeTradeRecords(): Flow<List<TradeRecord>>

    suspend fun upsertPosition(position: Position)

    suspend fun deletePosition(symbol: String)

    suspend fun insertTradeRecord(record: TradeRecord)

    suspend fun deleteTradeRecord(recordKey: String)

    suspend fun resetPortfolioData()
}