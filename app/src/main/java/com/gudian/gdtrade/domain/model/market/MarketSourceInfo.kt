package com.gudian.gdtrade.domain.model.market

import java.time.Duration
import java.time.Instant

data class MarketSourceInfo(
    val providerId: String,
    val sourceType: MarketSourceType,
    val supportsRealtime: Boolean,
    val latency: Duration?,
    val description: String,
    val receivedAt: Instant
) {
    init {
        require(providerId.isNotBlank()) { "providerId 不能为空" }
        require(description.isNotBlank()) { "description 不能为空" }
        require(latency == null || !latency.isNegative) { "latency 不能为负数" }
        require(sourceType == MarketSourceType.REMOTE || !supportsRealtime) {
            "非远程来源不能声明支持实时行情"
        }
    }
}