package com.gudian.gdtrade.data.repository

import com.gudian.gdtrade.domain.model.AccountGoal
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.TradeRecord
import kotlinx.coroutines.flow.Flow

interface PortfolioRepository {
    fun observeAccountGoals(): Flow<List<AccountGoal>>

    fun observePositions(): Flow<List<Position>>

    fun observeTradeRecords(): Flow<List<TradeRecord>>

    suspend fun addPosition(position: Position)

    suspend fun removePosition(symbol: String)

    suspend fun addTradeRecord(record: TradeRecord)

    suspend fun removeTradeRecord(recordKey: String)

    suspend fun resetPortfolioData()
}
