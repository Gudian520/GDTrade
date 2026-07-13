package com.gudian.gdtrade.data.repository

import android.content.Context
import com.gudian.gdtrade.domain.model.AccountGoal
import com.gudian.gdtrade.domain.model.MarketQuote
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.SignalStatus
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord
import com.gudian.gdtrade.domain.model.TradeSide
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class LocalPreferenceRepository(context: Context) : PortfolioRepository, MarketRepository {
    private val preferences = context.getSharedPreferences("gd_trade_local_data", Context.MODE_PRIVATE)
    private val positions = MutableStateFlow(loadPositions())
    private val candidates = MutableStateFlow(loadCandidates())
    private val tradeRecords = MutableStateFlow(loadTradeRecords())

    override fun observeAccountGoals(): Flow<List<AccountGoal>> {
        return MutableStateFlow(listOf(11500, 12500, 13800, 15000).map { AccountGoal(it, false) })
    }

    override fun observePositions(): Flow<List<Position>> = positions

    override fun observeTradeRecords(): Flow<List<TradeRecord>> = tradeRecords

    override fun observeQuotes(symbols: List<String>): Flow<List<MarketQuote>> {
        return positions.map { currentPositions ->
            val requestedSymbols = if (symbols.isEmpty()) currentPositions.map { it.symbol } else symbols
            requestedSymbols.distinct().mapNotNull { symbol -> defaultQuotes[symbol] }
        }
    }

    override fun observeCandidates(): Flow<List<StockCandidate>> = candidates

    override suspend fun addPosition(position: Position) {
        val normalized = position.copy(symbol = position.symbol.trim(), name = position.name.trim())
        if (normalized.symbol.isBlank() || normalized.name.isBlank() || normalized.quantity <= 0) return
        positions.value = positions.value.filterNot { it.symbol == normalized.symbol } + normalized
        savePositions()
    }

    override suspend fun removePosition(symbol: String) {
        positions.value = positions.value.filterNot { it.symbol == symbol }
        savePositions()
    }

    override suspend fun addTradeRecord(record: TradeRecord) {
        if (record.symbol.isBlank() || record.name.isBlank() || record.quantity <= 0 || record.price <= 0.0) return
        tradeRecords.value = listOf(record) + tradeRecords.value
        saveTradeRecords()
    }

    override suspend fun removeTradeRecord(recordKey: String) {
        tradeRecords.value = tradeRecords.value.filterNot { it.localKey == recordKey }
        saveTradeRecords()
    }

    override suspend fun resetPortfolioData() {
        positions.value = defaultPositions
        tradeRecords.value = defaultTradeRecords
        savePositions()
        saveTradeRecords()
    }

    override suspend fun addCandidate(candidate: StockCandidate) {
        val normalized = candidate.copy(symbol = candidate.symbol.trim(), name = candidate.name.trim())
        if (normalized.symbol.isBlank() || normalized.name.isBlank()) return
        candidates.value = candidates.value.filterNot { it.symbol == normalized.symbol } + normalized
        saveCandidates()
    }

    override suspend fun removeCandidate(symbol: String) {
        candidates.value = candidates.value.filterNot { it.symbol == symbol }
        saveCandidates()
    }

    override suspend fun resetMarketData() {
        candidates.value = defaultCandidates
        saveCandidates()
    }

    private fun loadPositions(): List<Position> {
        return preferences.getString(KEY_POSITIONS, null)?.takeIf { it.isNotBlank() }?.let { raw ->
            raw.split(ROW_SEPARATOR).mapNotNull { row ->
                val parts = row.split(COLUMN_SEPARATOR)
                if (parts.size != 4) return@mapNotNull null
                Position(
                    symbol = parts[0].decodeValue(),
                    name = parts[1].decodeValue(),
                    quantity = parts[2].toIntOrNull() ?: return@mapNotNull null,
                    note = parts[3].decodeValue()
                )
            }
        } ?: defaultPositions
    }

    private fun loadCandidates(): List<StockCandidate> {
        return preferences.getString(KEY_CANDIDATES, null)?.takeIf { it.isNotBlank() }?.let { raw ->
            raw.split(ROW_SEPARATOR).mapNotNull { row ->
                val parts = row.split(COLUMN_SEPARATOR)
                if (parts.size != 6) return@mapNotNull null
                StockCandidate(
                    symbol = parts[0].decodeValue(),
                    name = parts[1].decodeValue(),
                    theme = parts[2].decodeValue(),
                    reason = parts[3].decodeValue(),
                    signalStatus = runCatching { SignalStatus.valueOf(parts[4]) }.getOrDefault(SignalStatus.WatchOnly),
                    riskDeniedBuy = parts[5].toBooleanStrictOrNull() ?: false
                )
            }
        } ?: defaultCandidates
    }

    private fun loadTradeRecords(): List<TradeRecord> {
        return preferences.getString(KEY_TRADES, null)?.takeIf { it.isNotBlank() }?.let { raw ->
            raw.split(ROW_SEPARATOR).mapNotNull { row ->
                val parts = row.split(COLUMN_SEPARATOR)
                if (parts.size != 6) return@mapNotNull null
                TradeRecord(
                    tradeDate = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return@mapNotNull null,
                    symbol = parts[1].decodeValue(),
                    name = parts[2].decodeValue(),
                    side = runCatching { TradeSide.valueOf(parts[3]) }.getOrDefault(TradeSide.Buy),
                    price = parts[4].toDoubleOrNull() ?: return@mapNotNull null,
                    quantity = parts[5].toIntOrNull() ?: return@mapNotNull null,
                    note = parts[6].decodeValue()
                )
            }
        } ?: defaultTradeRecords
    }

    private fun savePositions() {
        val raw = positions.value.joinToString(ROW_SEPARATOR) { position ->
            listOf(
                position.symbol.encodeValue(),
                position.name.encodeValue(),
                position.quantity.toString(),
                position.note.encodeValue()
            ).joinToString(COLUMN_SEPARATOR)
        }
        preferences.edit().putString(KEY_POSITIONS, raw).apply()
    }

    private fun saveCandidates() {
        val raw = candidates.value.joinToString(ROW_SEPARATOR) { candidate ->
            listOf(
                candidate.symbol.encodeValue(),
                candidate.name.encodeValue(),
                candidate.theme.encodeValue(),
                candidate.reason.encodeValue(),
                candidate.signalStatus.name,
                candidate.riskDeniedBuy.toString()
            ).joinToString(COLUMN_SEPARATOR)
        }
        preferences.edit().putString(KEY_CANDIDATES, raw).apply()
    }

    private fun saveTradeRecords() {
        val raw = tradeRecords.value.joinToString(ROW_SEPARATOR) { record ->
            listOf(
                record.tradeDate.toString(),
                record.symbol.encodeValue(),
                record.name.encodeValue(),
                record.side.name,
                record.price.toString(),
                record.quantity.toString(),
                record.note.encodeValue()
            ).joinToString(COLUMN_SEPARATOR)
        }
        preferences.edit().putString(KEY_TRADES, raw).apply()
    }

    private fun String.encodeValue(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private fun String.decodeValue(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.name())

    companion object {
        private const val KEY_POSITIONS = "positions"
        private const val KEY_CANDIDATES = "candidates"
        private const val KEY_TRADES = "trade_records"
        private const val ROW_SEPARATOR = "\u001E"
        private const val COLUMN_SEPARATOR = "\u001F"

        val defaultPositions = listOf(
            Position("002185", "华天科技", 200, "当前持仓，V1只做监控与提醒。"),
            Position("515070", "华夏中证人工智能主题ETF", 800, "当前持仓，跟踪主题风险。"),
            Position("000725", "京东方A", 100, "当前持仓，减仓后观察。")
        )

        val defaultTradeRecords = listOf(
            TradeRecord(LocalDate.of(2026, 7, 13), "002185", "华天科技", TradeSide.Sell, 24.62, 100, "已知交易记录"),
            TradeRecord(LocalDate.of(2026, 7, 13), "000725", "京东方A", TradeSide.Sell, 6.92, 200, "已知交易记录")
        )

        val defaultCandidates = listOf(
            StockCandidate("002185", "华天科技", "半导体封测", "持仓仍在观察区，卖出后优先跟踪风险释放情况。", SignalStatus.HoldWatch, false),
            StockCandidate("515070", "华夏中证人工智能主题ETF", "人工智能主题", "主题弹性较高，V1仅提示观察，不形成自动交易。", SignalStatus.WaitPullback, true),
            StockCandidate("000725", "京东方A", "面板与消费电子", "已有减仓记录，继续等待结构确认。", SignalStatus.ReduceRisk, true),
            StockCandidate("300750", "宁德时代", "新能源权重观察", "仅作为动态观察池样例，用于验证候选股票扩展能力。", SignalStatus.WatchOnly, false)
        )

        private val defaultQuotes = listOf(
            MarketQuote("002185", "华天科技", 24.62, null, "V1静态样例，非实时行情", false),
            MarketQuote("515070", "华夏中证人工智能主题ETF", null, null, "V1静态样例，非实时行情", false),
            MarketQuote("000725", "京东方A", 6.92, null, "V1静态样例，非实时行情", false)
        ).associateBy { it.symbol }
    }
}

val TradeRecord.localKey: String
    get() = listOf(tradeDate, symbol, side, price, quantity, note).joinToString("|")
