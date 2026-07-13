package com.gudian.gdtrade.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.gudian.gdtrade.data.local.DefaultLocalData
import com.gudian.gdtrade.data.local.GdTradeDatabase
import com.gudian.gdtrade.data.local.migration.LegacyPreferencesMigrator
import com.gudian.gdtrade.data.local.storageKey
import com.gudian.gdtrade.data.local.toDomain
import com.gudian.gdtrade.data.local.toEntity
import com.gudian.gdtrade.domain.model.AccountGoal
import com.gudian.gdtrade.domain.model.MarketQuote
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

open class RoomTradeRepository(context: Context) : PortfolioRepository, MarketRepository {
    private val applicationContext = context.applicationContext
    private val database = GdTradeDatabase.getInstance(applicationContext)
    private val positionDao = database.positionDao()
    private val tradeRecordDao = database.tradeRecordDao()
    private val stockCandidateDao = database.stockCandidateDao()
    private val legacyMigrator = LegacyPreferencesMigrator(applicationContext, database)
    private val marketRefreshRequests = MutableStateFlow(0)

    override fun observeAccountGoals(): Flow<List<AccountGoal>> {
        return flowOf(listOf(11500, 12500, 13800, 15000).map { AccountGoal(it, false) })
    }

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

    override fun observeQuotes(symbols: List<String>): Flow<List<MarketQuote>> {
        return combine(observePositions(), marketRefreshRequests) { currentPositions, _ ->
            val requestedSymbols = if (symbols.isEmpty()) {
                currentPositions.map { it.symbol }
            } else {
                symbols
            }
            QuoteRequest(
                symbols = requestedSymbols.distinct().filter { it.isNotBlank() },
                positions = currentPositions
            )
        }.map { request ->
            if (request.symbols.isEmpty()) {
                emptyList()
            } else {
                fetchTencentQuotes(request.symbols, request.positions)
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun observeCandidates(): Flow<List<StockCandidate>> {
        return afterLegacyMigration {
            stockCandidateDao.observeAll().map { entities -> entities.map { it.toDomain() } }
        }
    }

    override suspend fun addPosition(position: Position) {
        ensureLegacyMigration()
        val normalized = position.copy(
            symbol = position.symbol.trim(),
            name = position.name.trim()
        )
        if (normalized.symbol.isBlank() || normalized.name.isBlank() || normalized.quantity <= 0) return
        positionDao.upsert(
            normalized.toEntity(positionDao.maxDisplayOrder() + 1)
        )
    }

    override suspend fun removePosition(symbol: String) {
        ensureLegacyMigration()
        positionDao.deleteBySymbol(symbol)
    }

    override suspend fun addTradeRecord(record: TradeRecord) {
        ensureLegacyMigration()
        if (
            record.symbol.isBlank() ||
            record.name.isBlank() ||
            record.quantity <= 0 ||
            record.price <= 0.0
        ) {
            return
        }
        tradeRecordDao.insert(record.toEntity())
    }

    override suspend fun removeTradeRecord(recordKey: String) {
        ensureLegacyMigration()
        tradeRecordDao.deleteByRecordKey(recordKey)
    }

    override suspend fun resetPortfolioData() {
        ensureLegacyMigration()
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

    override suspend fun addCandidate(candidate: StockCandidate) {
        ensureLegacyMigration()
        val normalized = candidate.copy(
            symbol = candidate.symbol.trim(),
            name = candidate.name.trim()
        )
        if (normalized.symbol.isBlank() || normalized.name.isBlank()) return
        stockCandidateDao.upsert(
            normalized.toEntity(stockCandidateDao.maxDisplayOrder() + 1)
        )
    }

    override suspend fun removeCandidate(symbol: String) {
        ensureLegacyMigration()
        stockCandidateDao.deleteBySymbol(symbol)
    }

    override suspend fun refreshMarketQuotes() {
        marketRefreshRequests.value += 1
    }

    override suspend fun resetMarketData() {
        ensureLegacyMigration()
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
            ensureLegacyMigration()
            emitAll(source())
        }
    }

    private suspend fun ensureLegacyMigration() {
        legacyMigrator.migrateIfNeeded()
    }

    private fun fetchTencentQuotes(
        symbols: List<String>,
        currentPositions: List<Position>
    ): List<MarketQuote> {
        return runCatching {
            val query = symbols.joinToString(",") { it.tencentCode }
            val connection = URL("https://qt.gtimg.cn/q=$query")
                .openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "GDTrade-Android-V1.5")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    return@runCatching fallbackQuotes(symbols, currentPositions)
                }
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseTencentQuotes(body).ifEmpty {
                    fallbackQuotes(symbols, currentPositions)
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse {
            fallbackQuotes(symbols, currentPositions)
        }
    }

    private fun parseTencentQuotes(body: String): List<MarketQuote> {
        return body.lineSequence().mapNotNull { line ->
            val payload = line.substringAfter("=\"", missingDelimiterValue = "")
                .substringBefore("\";", missingDelimiterValue = "")
            if (payload.isBlank()) return@mapNotNull null

            val fields = payload.split("~")
            val symbol = fields.getOrNull(2).orEmpty()
            val name = fields.getOrNull(1).orEmpty()
            val price = fields.getOrNull(3)?.toDoubleOrNull()
            val changePercent = fields.getOrNull(32)?.toDoubleOrNull()
            val quoteTime = fields.getOrNull(30).orEmpty()
            if (symbol.isBlank() || name.isBlank()) return@mapNotNull null

            MarketQuote(
                symbol = symbol,
                name = name,
                lastPrice = price,
                changePercent = changePercent,
                sourceLabel = "腾讯行情接口，时间 $quoteTime，可能延迟",
                isRealtime = false
            )
        }.toList()
    }

    private fun fallbackQuotes(
        symbols: List<String>,
        currentPositions: List<Position>
    ): List<MarketQuote> {
        return symbols.map { symbol ->
            DefaultLocalData.fallbackQuotes[symbol] ?: MarketQuote(
                symbol = symbol,
                name = currentPositions.firstOrNull { it.symbol == symbol }?.name.orEmpty(),
                lastPrice = null,
                changePercent = null,
                sourceLabel = "行情接口暂不可用，未使用实时行情",
                isRealtime = false
            )
        }
    }

    private data class QuoteRequest(
        val symbols: List<String>,
        val positions: List<Position>
    )
}

private val String.tencentCode: String
    get() = when {
        startsWith("6") || startsWith("5") -> "sh$this"
        else -> "sz$this"
    }

internal val TradeRecord.localKey: String
    get() = storageKey
