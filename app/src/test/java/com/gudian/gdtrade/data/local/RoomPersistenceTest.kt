package com.gudian.gdtrade.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gudian.gdtrade.data.local.entity.PositionEntity
import com.gudian.gdtrade.data.local.entity.StockCandidateEntity
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator.Companion.COLUMN_SEPARATOR
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator.Companion.KEY_CANDIDATES
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator.Companion.KEY_POSITIONS
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator.Companion.KEY_ROOM_MIGRATION_COMPLETE
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator.Companion.KEY_TRADES
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator.Companion.PREFERENCES_NAME
import com.gudian.gdtrade.domain.model.SignalStatus
import com.gudian.gdtrade.domain.model.TradeRecord
import com.gudian.gdtrade.domain.model.TradeSide
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomPersistenceTest {
    private lateinit var context: Context
    private lateinit var database: GdTradeDatabase

    @Before
    fun setUpDatabase() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        database = Room.inMemoryDatabaseBuilder(context, GdTradeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDownDatabase() {
        database.close()
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun daoSupportsThreeDataTypes() = runBlocking {
        database.positionDao().upsertAll(
            listOf(
                PositionEntity("000725", "京东方A", 100, "后显示", 1),
                PositionEntity("002185", "华天科技", 200, "先显示", 0)
            )
        )
        database.stockCandidateDao().upsert(
            StockCandidateEntity(
                symbol = "300750",
                name = "宁德时代",
                theme = "新能源",
                reason = "仅观察",
                signalStatus = SignalStatus.WatchOnly.name,
                riskDeniedBuy = true,
                displayOrder = 0
            )
        )
        val older = TradeRecord(
            LocalDate.of(2026, 7, 12),
            "002185",
            "华天科技",
            TradeSide.Buy,
            20.0,
            100,
            "较早记录"
        )
        val newer = TradeRecord(
            LocalDate.of(2026, 7, 13),
            "002185",
            "华天科技",
            TradeSide.Sell,
            24.62,
            100,
            "较新记录"
        )
        database.tradeRecordDao().insert(older.toEntity())
        database.tradeRecordDao().insert(newer.toEntity())

        val positions = database.positionDao().observeAll().first()
        val candidates = database.stockCandidateDao().observeAll().first()
        val records = database.tradeRecordDao().observeAll().first()

        assertEquals(listOf("002185", "000725"), positions.map { it.symbol })
        assertEquals("300750", candidates.single().symbol)
        assertEquals(listOf("较新记录", "较早记录"), records.map { it.note })

        database.positionDao().deleteBySymbol("002185")
        database.stockCandidateDao().deleteBySymbol("300750")
        database.tradeRecordDao().deleteByRecordKey(newer.storageKey)

        assertEquals(listOf("000725"), database.positionDao().observeAll().first().map { it.symbol })
        assertTrue(database.stockCandidateDao().observeAll().first().isEmpty())
        assertEquals(listOf("较早记录"), database.tradeRecordDao().observeAll().first().map { it.note })
    }

    @Test
    fun legacyDataIsMigratedOnFirstUpgrade() = runBlocking {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit()
            .putString(
                KEY_POSITIONS,
                legacyRow("002185".encoded(), "华天科技".encoded(), "300", "旧持仓备注".encoded())
            )
            .putString(
                KEY_CANDIDATES,
                legacyRow(
                    "300750".encoded(),
                    "宁德时代".encoded(),
                    "新能源".encoded(),
                    "等待回调".encoded(),
                    SignalStatus.WaitPullback.name,
                    "true"
                )
            )
            .putString(
                KEY_TRADES,
                legacyRow(
                    "2026-07-13",
                    "000725".encoded(),
                    "京东方A".encoded(),
                    TradeSide.Sell.name,
                    "6.92",
                    "200",
                    "旧交易记录".encoded()
                )
            )
            .commit()

        LegacyPreferencesMigrator(context, database).migrateIfNeeded()

        val position = database.positionDao().observeAll().first().single().toDomain()
        val candidate = database.stockCandidateDao().observeAll().first().single().toDomain()
        val record = database.tradeRecordDao().observeAll().first().single().toDomain()

        assertEquals("华天科技", position.name)
        assertEquals(300, position.quantity)
        assertEquals(SignalStatus.WaitPullback, candidate.signalStatus)
        assertTrue(candidate.riskDeniedBuy)
        assertEquals(LocalDate.of(2026, 7, 13), record.tradeDate)
        assertEquals(TradeSide.Sell, record.side)
        assertTrue(preferences.getBoolean(KEY_ROOM_MIGRATION_COMPLETE, false))
        assertTrue(preferences.contains(KEY_POSITIONS))
    }

    @Test
    fun completedMigrationDoesNotOverwriteRoomData() = runBlocking {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit()
            .putString(
                KEY_POSITIONS,
                legacyRow("002185".encoded(), "华天科技".encoded(), "200", "首次数据".encoded())
            )
            .commit()

        LegacyPreferencesMigrator(context, database).migrateIfNeeded()
        database.positionDao().upsert(
            PositionEntity("000725", "京东方A", 100, "Room 新数据", 1)
        )
        preferences.edit()
            .putString(
                KEY_POSITIONS,
                legacyRow("300750".encoded(), "宁德时代".encoded(), "50", "不应再次导入".encoded())
            )
            .commit()

        LegacyPreferencesMigrator(context, database).migrateIfNeeded()

        val symbols = database.positionDao().observeAll().first().map { it.symbol }
        assertEquals(listOf("002185", "000725"), symbols)
        assertFalse("300750" in symbols)
    }

    @Test
    fun emptyLegacyListsRemainEmpty() = runBlocking {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit()
            .putString(KEY_POSITIONS, "")
            .putString(KEY_CANDIDATES, "")
            .putString(KEY_TRADES, "")
            .commit()

        LegacyPreferencesMigrator(context, database).migrateIfNeeded()

        assertTrue(database.positionDao().observeAll().first().isEmpty())
        assertTrue(database.stockCandidateDao().observeAll().first().isEmpty())
        assertTrue(database.tradeRecordDao().observeAll().first().isEmpty())
    }

    @Test
    fun defaultDataIsCreatedWithoutLegacyData() = runBlocking {
        LegacyPreferencesMigrator(context, database).migrateIfNeeded()

        assertEquals(DefaultLocalData.positions.size, database.positionDao().count())
        assertEquals(DefaultLocalData.candidates.size, database.stockCandidateDao().count())
        assertEquals(DefaultLocalData.tradeRecords.size, database.tradeRecordDao().count())
        assertTrue(DefaultLocalData.fallbackQuotes.values.all { !it.isRealtime })
    }

    private fun legacyRow(vararg columns: String): String {
        return columns.joinToString(COLUMN_SEPARATOR)
    }

    private fun String.encoded(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8.name())
    }
}
