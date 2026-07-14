package com.gudian.gdtrade.data.local.datasource

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gudian.gdtrade.data.local.GdTradeDatabase
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator.Companion.COLUMN_SEPARATOR
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator.Companion.KEY_CANDIDATES
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator.Companion.KEY_POSITIONS
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator.Companion.KEY_ROOM_MIGRATION_COMPLETE
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator.Companion.KEY_TRADES
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator.Companion.PREFERENCES_NAME
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.SignalStatus
import com.gudian.gdtrade.domain.model.StockCandidate
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
class LocalDataSourceTest {
    private lateinit var context: Context
    private lateinit var database: GdTradeDatabase
    private lateinit var dataSource: RoomLocalDataSource

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString(KEY_POSITIONS, "")
            .putString(KEY_TRADES, "")
            .putString(KEY_CANDIDATES, "")
            .commit()
        database = Room.inMemoryDatabaseBuilder(context, GdTradeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataSource = RoomLocalDataSource(
            database = database,
            migrationCoordinator = LegacyPreferencesMigrator(context, database)
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun readsPositionsFromRoomAfterLegacyMigration() = runBlocking {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit()
            .putString(
                KEY_POSITIONS,
                legacyRow("002185".encoded(), "华天科技".encoded(), "300", "迁移持仓".encoded())
            )
            .commit()

        val positions = dataSource.observePositions().first()

        assertEquals(listOf(Position("002185", "华天科技", 300, "迁移持仓")), positions)
        assertTrue(preferences.getBoolean(KEY_ROOM_MIGRATION_COMPLETE, false))
    }

    @Test
    fun insertsTradeRecord() = runBlocking {
        val record = TradeRecord(
            tradeDate = LocalDate.of(2026, 7, 14),
            symbol = "000725",
            name = "京东方A",
            side = TradeSide.Sell,
            price = 6.92,
            quantity = 200,
            note = "本地数据源测试"
        )

        dataSource.insertTradeRecord(record)

        assertEquals(listOf(record), dataSource.observeTradeRecords().first())
    }

    @Test
    fun deletesPositionBySymbol() = runBlocking {
        dataSource.upsertPosition(Position("002185", "华天科技", 200, "待删除"))
        dataSource.upsertPosition(Position("000725", "京东方A", 100, "保留"))

        dataSource.deletePosition("002185")

        assertEquals(listOf("000725"), dataSource.observePositions().first().map { it.symbol })
    }

    @Test
    fun addsAndRemovesWatchlistCandidate() = runBlocking {
        val candidate = StockCandidate(
            symbol = "300750",
            name = "宁德时代",
            theme = "新能源",
            reason = "本地数据源测试",
            signalStatus = SignalStatus.WatchOnly,
            riskDeniedBuy = false
        )

        dataSource.upsertCandidate(candidate)
        assertEquals(listOf(candidate), dataSource.observeCandidates().first())

        dataSource.deleteCandidate(candidate.symbol)
        assertTrue(dataSource.observeCandidates().first().isEmpty())
    }

    @Test
    fun completedMigrationIsNotAppliedAgain() = runBlocking {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit()
            .putString(
                KEY_POSITIONS,
                legacyRow("002185".encoded(), "华天科技".encoded(), "200", "首次迁移".encoded())
            )
            .commit()

        assertEquals("002185", dataSource.observePositions().first().single().symbol)
        assertTrue(preferences.getBoolean(KEY_ROOM_MIGRATION_COMPLETE, false))

        preferences.edit()
            .putString(
                KEY_POSITIONS,
                legacyRow("300750".encoded(), "宁德时代".encoded(), "50", "不应再次迁移".encoded())
            )
            .commit()

        val positions = dataSource.observePositions().first()

        assertEquals(listOf("002185"), positions.map { it.symbol })
        assertFalse(positions.any { it.symbol == "300750" })
    }

    private fun legacyRow(vararg columns: String): String {
        return columns.joinToString(COLUMN_SEPARATOR)
    }

    private fun String.encoded(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8.name())
    }
}