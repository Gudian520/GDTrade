package com.gudian.gdtrade.data.local.datasource

import com.gudian.gdtrade.domain.model.StockCandidate
import kotlinx.coroutines.flow.Flow

interface WatchlistLocalDataSource {
    fun observeCandidates(): Flow<List<StockCandidate>>

    suspend fun upsertCandidate(candidate: StockCandidate)

    suspend fun deleteCandidate(symbol: String)

    suspend fun resetWatchlist()
}