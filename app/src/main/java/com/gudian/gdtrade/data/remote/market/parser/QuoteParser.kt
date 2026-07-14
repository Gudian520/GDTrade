package com.gudian.gdtrade.data.remote.market.parser

import com.gudian.gdtrade.data.remote.market.dto.RemoteAmountUnit
import com.gudian.gdtrade.data.remote.market.dto.RemotePercentUnit
import com.gudian.gdtrade.data.remote.market.dto.RemotePriceUnit
import com.gudian.gdtrade.data.remote.market.dto.RemoteQuoteDTO
import com.gudian.gdtrade.data.remote.market.dto.RemoteQuoteSourceKind
import com.gudian.gdtrade.data.remote.market.dto.RemoteQuoteTimeFormat
import com.gudian.gdtrade.data.remote.market.dto.RemoteVolumeUnit
import com.gudian.gdtrade.data.remote.market.error.RemoteMarketError

data class QuoteParseResult(
    val quotes: List<RemoteQuoteDTO>,
    val errors: List<RemoteMarketError>
)

/** 腾讯文本行情协议解析器；不发起网络请求，不生成 fallback。 */
class QuoteParser {
    fun parseTencent(body: String): QuoteParseResult {
        if (body.isBlank()) {
            return QuoteParseResult(
                quotes = emptyList(),
                errors = listOf(RemoteMarketError.EmptyResponse(TENCENT_PROVIDER_ID))
            )
        }

        val records = body.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val errors = mutableListOf<RemoteMarketError>()
        val quotes = records.mapIndexedNotNull { index, record ->
            val match = TENCENT_RECORD.matchEntire("$record;")
            if (match == null) {
                errors += RemoteMarketError.MalformedResponse(
                    providerId = TENCENT_PROVIDER_ID,
                    recordNumber = index + 1
                )
                return@mapIndexedNotNull null
            }
            val recordId = match.groupValues[1]
            val fields = match.groupValues[2].split("~")
            RemoteQuoteDTO(
                providerId = TENCENT_PROVIDER_ID,
                providerSymbol = fields.getOrNull(SYMBOL_INDEX)
                    ?.takeIf { it.isNotBlank() }
                    ?: recordId.removePrefix("sh").removePrefix("sz").takeIf { it.isNotBlank() },
                name = fields.getOrNull(NAME_INDEX)?.takeIf { it.isNotBlank() },
                lastPrice = fields.getOrNull(LAST_PRICE_INDEX)?.takeIf { it.isNotBlank() },
                lastPriceUnit = RemotePriceUnit.YUAN,
                changePercent = fields.getOrNull(CHANGE_PERCENT_INDEX)?.takeIf { it.isNotBlank() },
                changePercentUnit = RemotePercentUnit.PERCENT,
                volume = fields.getOrNull(VOLUME_INDEX)?.takeIf { it.isNotBlank() },
                volumeUnit = RemoteVolumeUnit.LOT_100,
                turnoverAmount = fields.getOrNull(TURNOVER_AMOUNT_INDEX)?.takeIf { it.isNotBlank() },
                turnoverAmountUnit = RemoteAmountUnit.TEN_THOUSAND_YUAN,
                turnoverRate = fields.getOrNull(TURNOVER_RATE_INDEX)?.takeIf { it.isNotBlank() },
                turnoverRateUnit = RemotePercentUnit.PERCENT,
                quoteTime = fields.getOrNull(QUOTE_TIME_INDEX)?.takeIf { it.isNotBlank() },
                quoteTimeFormat = RemoteQuoteTimeFormat.ASIA_SHANGHAI_COMPACT,
                sourceKind = RemoteQuoteSourceKind.REMOTE,
                supportsRealtime = false,
                declaredLatencySeconds = null,
                sourceDescription = "腾讯行情接口，时效能力未验证",
                vendorFields = buildMap {
                    put("providerRecordId", recordId)
                    fields.forEachIndexed { index, value -> put("field_$index", value) }
                }
            )
        }

        return QuoteParseResult(quotes = quotes, errors = errors)
    }

    companion object {
        const val TENCENT_PROVIDER_ID: String = "TENCENT_QT"

        private val TENCENT_RECORD = Regex("""v_([^=\s]+)=\"([^\"]*)\";""")
        private const val NAME_INDEX = 1
        private const val SYMBOL_INDEX = 2
        private const val LAST_PRICE_INDEX = 3
        private const val VOLUME_INDEX = 6
        private const val QUOTE_TIME_INDEX = 30
        private const val CHANGE_PERCENT_INDEX = 32
        private const val TURNOVER_AMOUNT_INDEX = 37
        private const val TURNOVER_RATE_INDEX = 38
    }
}
