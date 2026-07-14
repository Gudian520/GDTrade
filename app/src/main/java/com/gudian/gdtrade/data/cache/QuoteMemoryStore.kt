package com.gudian.gdtrade.data.cache

import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.StockQuote
import java.time.Duration
import java.time.Instant

/** 进程内行情缓存；不写入 Room，也不改变报价原有状态和来源。 */
class QuoteMemoryStore {
    data class Entry(
        val quote: StockQuote,
        val receivedAt: Instant
    )

    private val lock = Any()
    private val entries = linkedMapOf<String, Entry>()
    private var latestSuccessfulSnapshotAt: Instant? = null

    fun get(symbol: String): StockQuote? = synchronized(lock) {
        entries[symbol]?.quote
    }

    fun get(symbols: Set<String>): Map<String, StockQuote> = synchronized(lock) {
        symbols.mapNotNull { symbol ->
            entries[symbol]?.quote?.let { quote -> symbol to quote }
        }.toMap(linkedMapOf())
    }

    fun getEntries(symbols: Set<String>): Map<String, Entry> = synchronized(lock) {
        symbols.mapNotNull { symbol ->
            entries[symbol]?.let { entry -> symbol to entry }
        }.toMap(linkedMapOf())
    }

    fun getFresh(
        symbols: Set<String>,
        now: Instant,
        maxAge: Duration
    ): Map<String, StockQuote> = synchronized(lock) {
        symbols.mapNotNull { symbol ->
            val entry = entries[symbol] ?: return@mapNotNull null
            val age = Duration.between(entry.receivedAt, now)
            if (!age.isNegative && age <= maxAge) symbol to entry.quote else null
        }.toMap(linkedMapOf())
    }

    fun put(quotes: Collection<StockQuote>) = synchronized(lock) {
        quotes.forEach { quote ->
            if (quote.dataStatus == MarketDataStatus.ERROR || quote.dataStatus == MarketDataStatus.LOADING) {
                return@forEach
            }

            val current = entries[quote.symbol]?.quote
            val incomingIsMock = quote.dataStatus == MarketDataStatus.MOCK
            val currentIsRemoteData = current?.dataStatus == MarketDataStatus.SUCCESS ||
                current?.dataStatus == MarketDataStatus.DELAYED
            if (incomingIsMock && currentIsRemoteData) {
                return@forEach
            }

            entries[quote.symbol] = Entry(
                quote = quote,
                receivedAt = quote.source.receivedAt
            )
            if (quote.dataStatus == MarketDataStatus.SUCCESS || quote.dataStatus == MarketDataStatus.DELAYED) {
                val receivedAt = quote.source.receivedAt
                val latest = latestSuccessfulSnapshotAt
                if (latest == null || receivedAt.isAfter(latest)) {
                    latestSuccessfulSnapshotAt = receivedAt
                }
            }
        }
    }

    fun lastSuccessfulSnapshotAt(): Instant? = synchronized(lock) {
        latestSuccessfulSnapshotAt
    }
}
