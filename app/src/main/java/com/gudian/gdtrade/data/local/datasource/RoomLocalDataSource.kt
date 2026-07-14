package com.gudian.gdtrade.data.local.datasource

import android.content.Context
import androidx.room.withTransaction
import com.gudian.gdtrade.data.local.DefaultLocalData
import com.gudian.gdtrade.data.local.GdTradeDatabase
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator
import com.gudian.gdtrade.data.local.toDomain
import com.gudian.gdtrade.data.local.toEntity
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

internal class RoomLocalDataSource(
    private val database: GdTradeDatabase,
    private val migrationCoordinator: LegacyMigrationCoordinator
) : PortfolioLocalDataSource, WatchlistLocalDataSource {
    private val positionDao = database.positionDao()
    private val tradeRecordDao = database.tradeRecordDao()
    private val stockCandidateDao = database.stockCandidateDao()

    override fun observePositions(): Flow<List<Position>> {
        return afterLegacyMigration {
            positionDao.observeAll().map { entities -> entities.map { it.toDomain() } }
        }
    }

    override fun observeTradeRecords(): Flow<List<TradeRecord>> {
        return afterLegacyMigration {
            tradeRecordDao.observeAll().map { entities -> entities.map { it.toDomain() } }
        }
    }

    override suspend fun upsertPosition(position: Position) {
        migrationCoordinator.migrateIfNeeded()
        positionDao.upsert(position.toEntity(positionDao.maxDisplayOrder() + 1))
    }

    override suspend fun deletePosition(symbol: String) {
        migrationCoordinator.migrateIfNeeded()
        positionDao.deleteBySymbol(symbol)
    }

    override suspend fun insertTradeRecord(record: TradeRecord) {
        migrationCoordinator.migrateIfNeeded()
        tradeRecordDao.insert(record.toEntity())
    }

    override suspend fun deleteTradeRecord(recordKey: String) {
        migrationCoordinator.migrateIfNeeded()
        tradeRecordDao.deleteByRecordKey(recordKey)
    }

    override suspend fun resetPortfolioData() {
        migrationCoordinator.migrateIfNeeded()
        database.withTransaction {
            positionDao.deleteAll()
            tradeRecordDao.deleteAll()
            positionDao.upsertAll(
                DefaultLocalData.positions.mapIndexed { index, position ->
                    position.toEntity(index)
                }
            )
            tradeRecordDao.insertAll(
                DefaultLocalData.tradeRecords.asReversed().map { record -> record.toEntity() }
            )
        }
    }

    override fun observeCandidates(): Flow<List<StockCandidate>> {
        return afterLegacyMigration {
            stockCandidateDao.observeAll().map { entities -> entities.map { it.toDomain() } }
        }
    }

    override suspend fun upsertCandidate(candidate: StockCandidate) {
        migrationCoordinator.migrateIfNeeded()
        stockCandidateDao.upsert(candidate.toEntity(stockCandidateDao.maxDisplayOrder() + 1))
    }

    override suspend fun deleteCandidate(symbol: String) {
        migrationCoordinator.migrateIfNeeded()
        stockCandidateDao.deleteBySymbol(symbol)
    }

    override suspend fun resetWatchlist() {
        migrationCoordinator.migrateIfNeeded()
        database.withTransaction {
            stockCandidateDao.deleteAll()
            stockCandidateDao.upsertAll(
                DefaultLocalData.candidates.mapIndexed { index, candidate ->
                    candidate.toEntity(index)
                }
            )
        }
    }

    private fun <T> afterLegacyMigration(source: () -> Flow<T>): Flow<T> {
        return flow {
            migrationCoordinator.migrateIfNeeded()
            emitAll(source())
        }
    }

    companion object {
        fun create(context: Context): RoomLocalDataSource {
            val applicationContext = context.applicationContext
            val database = GdTradeDatabase.getInstance(applicationContext)
            return RoomLocalDataSource(
                database = database,
                migrationCoordinator = LegacyPreferencesMigrator(applicationContext, database)
            )
        }
    }
}