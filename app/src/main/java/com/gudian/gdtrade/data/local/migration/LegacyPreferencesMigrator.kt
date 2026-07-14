package com.gudian.gdtrade.data.local.migration

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.gudian.gdtrade.data.local.DefaultLocalData
import com.gudian.gdtrade.data.local.GdTradeDatabase
import com.gudian.gdtrade.data.local.datasource.LegacyMigrationCoordinator
import com.gudian.gdtrade.data.local.toEntity
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.SignalStatus
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord
import com.gudian.gdtrade.domain.model.TradeSide
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class LegacyPreferencesMigrator(
    context: Context,
    private val database: GdTradeDatabase,
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
) : LegacyMigrationCoordinator {
    private val migrationMutex = Mutex()

    override suspend fun migrateIfNeeded() {
        migrationMutex.withLock {
            if (preferences.getBoolean(KEY_ROOM_MIGRATION_COMPLETE, false)) return

            if (databaseContainsData()) {
                markMigrationComplete()
                return
            }

            val positions = readPositions()
            val candidates = readCandidates()
            val tradeRecords = readTradeRecords()

            database.withTransaction {
                database.positionDao().upsertAll(
                    positions.mapIndexed { index, position -> position.toEntity(index) }
                )
                database.stockCandidateDao().upsertAll(
                    candidates.mapIndexed { index, candidate -> candidate.toEntity(index) }
                )
                database.tradeRecordDao().insertAll(
                    tradeRecords.asReversed().map { record -> record.toEntity() }
                )
            }

            markMigrationComplete()
        }
    }

    private suspend fun databaseContainsData(): Boolean {
        return database.positionDao().count() > 0 ||
            database.stockCandidateDao().count() > 0 ||
            database.tradeRecordDao().count() > 0
    }

    private fun readPositions(): List<Position> {
        if (!preferences.contains(KEY_POSITIONS)) return DefaultLocalData.positions
        return preferences.getString(KEY_POSITIONS, null).orEmpty().rows().mapNotNull { row ->
            runCatching {
                val parts = row.split(COLUMN_SEPARATOR)
                if (parts.size != 4) return@runCatching null
                Position(
                    symbol = parts[0].decodeValue(),
                    name = parts[1].decodeValue(),
                    quantity = parts[2].toInt(),
                    note = parts[3].decodeValue()
                )
            }.getOrNull()
        }
    }

    private fun readCandidates(): List<StockCandidate> {
        if (!preferences.contains(KEY_CANDIDATES)) return DefaultLocalData.candidates
        return preferences.getString(KEY_CANDIDATES, null).orEmpty().rows().mapNotNull { row ->
            runCatching {
                val parts = row.split(COLUMN_SEPARATOR)
                if (parts.size != 6) return@runCatching null
                StockCandidate(
                    symbol = parts[0].decodeValue(),
                    name = parts[1].decodeValue(),
                    theme = parts[2].decodeValue(),
                    reason = parts[3].decodeValue(),
                    signalStatus = SignalStatus.valueOf(parts[4]),
                    riskDeniedBuy = parts[5].toBooleanStrict()
                )
            }.getOrNull()
        }
    }

    private fun readTradeRecords(): List<TradeRecord> {
        if (!preferences.contains(KEY_TRADES)) return DefaultLocalData.tradeRecords
        return preferences.getString(KEY_TRADES, null).orEmpty().rows().mapNotNull { row ->
            runCatching {
                val parts = row.split(COLUMN_SEPARATOR)
                if (parts.size != 7) return@runCatching null
                TradeRecord(
                    tradeDate = LocalDate.parse(parts[0]),
                    symbol = parts[1].decodeValue(),
                    name = parts[2].decodeValue(),
                    side = TradeSide.valueOf(parts[3]),
                    price = parts[4].toDouble(),
                    quantity = parts[5].toInt(),
                    note = parts[6].decodeValue()
                )
            }.getOrNull()
        }
    }

    private fun String.rows(): List<String> {
        return if (isBlank()) emptyList() else split(ROW_SEPARATOR)
    }

    private fun String.decodeValue(): String {
        return URLDecoder.decode(this, StandardCharsets.UTF_8.name())
    }

    private fun markMigrationComplete() {
        preferences.edit()
            .putBoolean(KEY_ROOM_MIGRATION_COMPLETE, true)
            .commit()
    }

    companion object {
        internal const val PREFERENCES_NAME = "gd_trade_local_data"
        internal const val KEY_POSITIONS = "positions"
        internal const val KEY_CANDIDATES = "candidates"
        internal const val KEY_TRADES = "trade_records"
        internal const val KEY_ROOM_MIGRATION_COMPLETE = "room_migration_v1_complete"
        internal const val ROW_SEPARATOR = "\u001E"
        internal const val COLUMN_SEPARATOR = "\u001F"
    }
}
