package com.gudian.gdtrade.data.remote.market.mapper

import com.gudian.gdtrade.data.remote.market.dto.RemoteAmountUnit
import com.gudian.gdtrade.data.remote.market.dto.RemotePercentUnit
import com.gudian.gdtrade.data.remote.market.dto.RemotePriceUnit
import com.gudian.gdtrade.data.remote.market.dto.RemoteQuoteDTO
import com.gudian.gdtrade.data.remote.market.dto.RemoteQuoteSourceKind
import com.gudian.gdtrade.data.remote.market.dto.RemoteQuoteTimeFormat
import com.gudian.gdtrade.data.remote.market.dto.RemoteVolumeUnit
import com.gudian.gdtrade.data.remote.market.error.RemoteMarketError
import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.model.market.MarketSourceInfo
import com.gudian.gdtrade.domain.model.market.MarketSourceType
import com.gudian.gdtrade.domain.model.market.StockQuote
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed interface RemoteQuoteMappingResult {
    data class Success(val quote: StockQuote) : RemoteQuoteMappingResult
    data class Failure(val error: RemoteMarketError) : RemoteQuoteMappingResult
}

/** 将供应商 DTO 严格归一化为 market-domain-v1。 */
class QuoteMapper {
    fun map(dto: RemoteQuoteDTO, receivedAt: Instant): RemoteQuoteMappingResult {
        return try {
            val symbol = normalizeSymbol(dto.providerSymbol)
            val name = dto.name?.trim().orEmpty().ifBlank {
                throw MappingFailure("name", "股票名称缺失")
            }
            val updatedAt = parseQuoteTime(dto.quoteTime, dto.quoteTimeFormat)
            val sourceType = dto.sourceKind.toDomainSourceType()
            val supportsRealtime = sourceType == MarketSourceType.REMOTE && dto.supportsRealtime
            val status = when (sourceType) {
                MarketSourceType.STATIC_SAMPLE,
                MarketSourceType.FALLBACK -> MarketDataStatus.MOCK
                MarketSourceType.REMOTE -> if (supportsRealtime && updatedAt != null) {
                    MarketDataStatus.SUCCESS
                } else {
                    MarketDataStatus.DELAYED
                }
                MarketSourceType.LOCAL_CACHE -> error("Remote DTO 不应产生本地缓存来源")
            }

            val providerId = when (sourceType) {
                MarketSourceType.STATIC_SAMPLE -> "STATIC_SAMPLE"
                MarketSourceType.FALLBACK -> "FALLBACK"
                MarketSourceType.REMOTE -> dto.providerId.trim().ifBlank {
                    throw MappingFailure("providerId", "远程来源标识缺失")
                }
                MarketSourceType.LOCAL_CACHE -> error("Remote DTO 不应产生本地缓存来源")
            }
            val latency = dto.declaredLatencySeconds?.let {
                if (it < 0L) throw MappingFailure("declaredLatencySeconds", "声明延迟不能为负数")
                Duration.ofSeconds(it)
            }

            RemoteQuoteMappingResult.Success(
                StockQuote(
                    symbol = symbol,
                    name = name,
                    lastPrice = convertPrice(dto.lastPrice, dto.lastPriceUnit),
                    changePercent = convertPercent(
                        dto.changePercent,
                        dto.changePercentUnit,
                        "changePercent"
                    ),
                    volume = convertVolume(dto.volume, dto.volumeUnit),
                    turnoverAmount = convertAmount(dto.turnoverAmount, dto.turnoverAmountUnit),
                    turnoverRate = convertPercent(
                        dto.turnoverRate,
                        dto.turnoverRateUnit,
                        "turnoverRate"
                    ),
                    updatedAt = updatedAt,
                    dataStatus = status,
                    source = MarketSourceInfo(
                        providerId = providerId,
                        sourceType = sourceType,
                        supportsRealtime = supportsRealtime,
                        latency = latency,
                        description = sourceDescription(dto, sourceType, providerId),
                        receivedAt = receivedAt
                    )
                )
            )
        } catch (failure: MappingFailure) {
            RemoteQuoteMappingResult.Failure(
                RemoteMarketError.InvalidField(
                    providerId = dto.providerId.takeIf { it.isNotBlank() },
                    symbol = dto.providerSymbol?.trim()?.takeIf { it.isNotBlank() },
                    fieldName = failure.fieldName,
                    reason = failure.reason
                )
            )
        } catch (_: ArithmeticException) {
            RemoteQuoteMappingResult.Failure(
                RemoteMarketError.InvalidField(
                    providerId = dto.providerId.takeIf { it.isNotBlank() },
                    symbol = dto.providerSymbol?.trim()?.takeIf { it.isNotBlank() },
                    fieldName = "numericValue",
                    reason = "数值超出支持范围"
                )
            )
        }
    }

    private fun normalizeSymbol(rawSymbol: String?): String {
        val normalized = rawSymbol?.trim()?.lowercase()
            ?.removePrefix("sh")
            ?.removePrefix("sz")
            .orEmpty()
        if (!SYMBOL_PATTERN.matches(normalized)) {
            throw MappingFailure("symbol", "股票代码必须为六位数字")
        }
        return normalized
    }

    private fun convertPrice(value: String?, unit: RemotePriceUnit): Double? {
        val decimal = parseOptionalDecimal(value, "lastPrice") ?: return null
        val yuan = when (unit) {
            RemotePriceUnit.YUAN -> decimal
            RemotePriceUnit.FEN -> decimal.divide(HUNDRED)
            RemotePriceUnit.UNKNOWN -> throw MappingFailure("lastPriceUnit", "价格单位未知")
        }
        if (yuan.signum() < 0) throw MappingFailure("lastPrice", "最新价格不能为负数")
        return yuan.toFiniteDouble("lastPrice")
    }

    private fun convertPercent(
        value: String?,
        unit: RemotePercentUnit,
        fieldName: String
    ): Double? {
        val decimal = parseOptionalDecimal(value, fieldName) ?: return null
        val percent = when (unit) {
            RemotePercentUnit.PERCENT -> decimal
            RemotePercentUnit.RATIO -> decimal.multiply(HUNDRED)
            RemotePercentUnit.UNKNOWN -> throw MappingFailure("${fieldName}Unit", "百分比单位未知")
        }
        return percent.toFiniteDouble(fieldName)
    }

    private fun convertVolume(value: String?, unit: RemoteVolumeUnit): Long? {
        val decimal = parseOptionalDecimal(value, "volume") ?: return null
        val normalized = when (unit) {
            RemoteVolumeUnit.SHARE_OR_UNIT -> decimal
            RemoteVolumeUnit.LOT_100 -> decimal.multiply(HUNDRED)
            RemoteVolumeUnit.UNKNOWN -> throw MappingFailure("volumeUnit", "成交量单位未知")
        }
        if (normalized.signum() < 0) throw MappingFailure("volume", "成交量不能为负数")
        return try {
            normalized.setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (_: ArithmeticException) {
            throw MappingFailure("volume", "成交量必须能精确转换为股或份")
        }
    }

    private fun convertAmount(value: String?, unit: RemoteAmountUnit): Double? {
        val decimal = parseOptionalDecimal(value, "turnoverAmount") ?: return null
        val yuan = when (unit) {
            RemoteAmountUnit.YUAN -> decimal
            RemoteAmountUnit.TEN_THOUSAND_YUAN -> decimal.multiply(TEN_THOUSAND)
            RemoteAmountUnit.HUNDRED_MILLION_YUAN -> decimal.multiply(HUNDRED_MILLION)
            RemoteAmountUnit.UNKNOWN -> throw MappingFailure("turnoverAmountUnit", "成交额单位未知")
        }
        if (yuan.signum() < 0) throw MappingFailure("turnoverAmount", "成交额不能为负数")
        return yuan.toFiniteDouble("turnoverAmount")
    }

    private fun parseOptionalDecimal(value: String?, fieldName: String): BigDecimal? {
        val normalized = value?.trim()?.removeSuffix("%")?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        return normalized.toBigDecimalOrNull()
            ?: throw MappingFailure(fieldName, "不是有效数值")
    }

    private fun parseQuoteTime(value: String?, format: RemoteQuoteTimeFormat): Instant? {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        return try {
            when (format) {
                RemoteQuoteTimeFormat.ISO_INSTANT -> Instant.parse(normalized)
                RemoteQuoteTimeFormat.ASIA_SHANGHAI_COMPACT -> LocalDateTime
                    .parse(normalized, TENCENT_TIME_FORMATTER)
                    .atZone(SHANGHAI_ZONE)
                    .toInstant()
                RemoteQuoteTimeFormat.UNKNOWN -> throw MappingFailure("quoteTimeFormat", "时间格式未知")
            }
        } catch (failure: MappingFailure) {
            throw failure
        } catch (_: RuntimeException) {
            throw MappingFailure("quoteTime", "不是有效的供应商行情时间")
        }
    }

    private fun RemoteQuoteSourceKind.toDomainSourceType(): MarketSourceType = when (this) {
        RemoteQuoteSourceKind.REMOTE -> MarketSourceType.REMOTE
        RemoteQuoteSourceKind.STATIC_SAMPLE -> MarketSourceType.STATIC_SAMPLE
        RemoteQuoteSourceKind.FALLBACK -> MarketSourceType.FALLBACK
    }

    private fun sourceDescription(
        dto: RemoteQuoteDTO,
        sourceType: MarketSourceType,
        providerId: String
    ): String {
        return when (sourceType) {
            MarketSourceType.REMOTE -> dto.sourceDescription
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: if (dto.supportsRealtime) {
                    "$providerId 远程行情"
                } else {
                    "$providerId 远程行情，时效能力未验证"
                }
            MarketSourceType.STATIC_SAMPLE -> "静态样例，非实时行情"
            MarketSourceType.FALLBACK -> "行情 fallback，非实时行情"
            MarketSourceType.LOCAL_CACHE -> error("Remote DTO 不应产生本地缓存来源")
        }
    }

    private fun BigDecimal.toFiniteDouble(fieldName: String): Double {
        val result = toDouble()
        if (!result.isFinite()) throw MappingFailure(fieldName, "数值超出支持范围")
        return result
    }

    private data class MappingFailure(
        val fieldName: String,
        val reason: String
    ) : RuntimeException()

    companion object {
        private val SYMBOL_PATTERN = Regex("\\d{6}")
        private val TENCENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        private val SHANGHAI_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        private val HUNDRED = BigDecimal("100")
        private val TEN_THOUSAND = BigDecimal("10000")
        private val HUNDRED_MILLION = BigDecimal("100000000")
    }
}
