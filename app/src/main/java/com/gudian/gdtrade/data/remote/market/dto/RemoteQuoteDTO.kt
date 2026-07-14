package com.gudian.gdtrade.data.remote.market.dto

/**
 * 供应商报价的中间数据结构。
 *
 * 数值和时间保留为供应商原始文本，避免 DTO 隐式携带领域单位或时间语义。
 * 未识别字段保存在 [vendorFields]，供应商协议变化只影响 Parser 和 Mapper。
 */
data class RemoteQuoteDTO(
    val providerId: String,
    val providerSymbol: String?,
    val name: String?,
    val lastPrice: String?,
    val lastPriceUnit: RemotePriceUnit = RemotePriceUnit.YUAN,
    val changePercent: String?,
    val changePercentUnit: RemotePercentUnit = RemotePercentUnit.PERCENT,
    val volume: String?,
    val volumeUnit: RemoteVolumeUnit = RemoteVolumeUnit.SHARE_OR_UNIT,
    val turnoverAmount: String?,
    val turnoverAmountUnit: RemoteAmountUnit = RemoteAmountUnit.YUAN,
    val turnoverRate: String?,
    val turnoverRateUnit: RemotePercentUnit = RemotePercentUnit.PERCENT,
    val quoteTime: String?,
    val quoteTimeFormat: RemoteQuoteTimeFormat = RemoteQuoteTimeFormat.ISO_INSTANT,
    val sourceKind: RemoteQuoteSourceKind = RemoteQuoteSourceKind.REMOTE,
    val supportsRealtime: Boolean = false,
    val declaredLatencySeconds: Long? = null,
    val sourceDescription: String? = null,
    val vendorFields: Map<String, String> = emptyMap()
)

enum class RemotePriceUnit {
    YUAN,
    FEN,
    UNKNOWN
}

enum class RemotePercentUnit {
    PERCENT,
    RATIO,
    UNKNOWN
}

enum class RemoteVolumeUnit {
    SHARE_OR_UNIT,
    LOT_100,
    UNKNOWN
}

enum class RemoteAmountUnit {
    YUAN,
    TEN_THOUSAND_YUAN,
    HUNDRED_MILLION_YUAN,
    UNKNOWN
}

enum class RemoteQuoteTimeFormat {
    ISO_INSTANT,
    ASIA_SHANGHAI_COMPACT,
    UNKNOWN
}

enum class RemoteQuoteSourceKind {
    REMOTE,
    STATIC_SAMPLE,
    FALLBACK
}
