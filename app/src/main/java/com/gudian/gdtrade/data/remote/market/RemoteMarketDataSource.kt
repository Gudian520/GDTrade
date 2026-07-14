package com.gudian.gdtrade.data.remote.market

import com.gudian.gdtrade.data.remote.market.error.RemoteMarketError
import com.gudian.gdtrade.data.remote.market.mapper.QuoteMapper
import com.gudian.gdtrade.data.remote.market.mapper.RemoteQuoteMappingResult
import com.gudian.gdtrade.data.remote.market.parser.QuoteParser
import com.gudian.gdtrade.data.remote.market.parser.QuoteParser.Companion.TENCENT_PROVIDER_ID
import com.gudian.gdtrade.domain.model.market.StockQuote
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

data class RemoteMarketResult(
    val requestedSymbols: Set<String>,
    val quotes: Map<String, StockQuote>,
    val errors: List<RemoteMarketError>
) {
    init {
        require(requestedSymbols.isNotEmpty()) { "远程行情请求不能为空" }
        require(quotes.keys.all { it in requestedSymbols }) { "远程行情结果包含未请求代码" }
        require(quotes.all { (symbol, quote) -> symbol == quote.symbol }) {
            "远程行情 Map 键必须与 StockQuote.symbol 一致"
        }
    }
}

interface RemoteMarketDataSource {
    suspend fun fetchQuotes(symbols: Set<String>): RemoteMarketResult
}

data class MarketHttpResponse(
    val statusCode: Int,
    val body: String
)

fun interface MarketHttpClient {
    suspend fun get(url: String): MarketHttpResponse
}

class UrlConnectionMarketHttpClient(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MarketHttpClient {
    override suspend fun get(url: String): MarketHttpResponse = withContext(ioDispatcher) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            setRequestProperty("User-Agent", "GDTrade-Android-V1.2")
        }
        try {
            val statusCode = connection.responseCode
            val body = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                ""
            }
            MarketHttpResponse(statusCode = statusCode, body = body)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 5_000
    }
}

/**
 * 腾讯行情远程数据源。
 *
 * 只负责网络、协议解析和领域映射，不访问 Room、持仓或观察池，也不决定 fallback。
 */
class TencentRemoteMarketDataSource(
    private val httpClient: MarketHttpClient = UrlConnectionMarketHttpClient(),
    private val parser: QuoteParser = QuoteParser(),
    private val mapper: QuoteMapper = QuoteMapper(),
    private val clock: Clock = Clock.systemUTC()
) : RemoteMarketDataSource {
    override suspend fun fetchQuotes(symbols: Set<String>): RemoteMarketResult {
        require(symbols.isNotEmpty()) { "远程行情请求不能为空" }
        val response = try {
            httpClient.get(buildUrl(symbols))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return RemoteMarketResult(
                requestedSymbols = symbols,
                quotes = emptyMap(),
                errors = listOf(RemoteMarketError.NetworkFailure(TENCENT_PROVIDER_ID))
            )
        }

        if (response.statusCode !in 200..299) {
            return RemoteMarketResult(
                requestedSymbols = symbols,
                quotes = emptyMap(),
                errors = listOf(
                    RemoteMarketError.HttpFailure(
                        providerId = TENCENT_PROVIDER_ID,
                        statusCode = response.statusCode
                    )
                )
            )
        }

        val parsed = parser.parseTencent(response.body)
        val errors = parsed.errors.toMutableList()
        val receivedAt = clock.instant()
        val quotes = linkedMapOf<String, StockQuote>()
        parsed.quotes.forEach { dto ->
            when (val mapped = mapper.map(dto, receivedAt)) {
                is RemoteQuoteMappingResult.Success -> {
                    if (mapped.quote.symbol in symbols) {
                        quotes[mapped.quote.symbol] = mapped.quote
                    }
                }

                is RemoteQuoteMappingResult.Failure -> errors += mapped.error
            }
        }

        return RemoteMarketResult(
            requestedSymbols = symbols,
            quotes = quotes,
            errors = errors
        )
    }

    private fun buildUrl(symbols: Set<String>): String {
        val query = symbols.joinToString(",") { symbol -> symbol.toTencentCode() }
        return "$TENCENT_ENDPOINT$query"
    }

    private fun String.toTencentCode(): String = when {
        startsWith("6") || startsWith("5") -> "sh$this"
        else -> "sz$this"
    }

    private companion object {
        const val TENCENT_ENDPOINT = "https://qt.gtimg.cn/q="
    }
}
