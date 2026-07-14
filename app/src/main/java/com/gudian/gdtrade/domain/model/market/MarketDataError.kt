package com.gudian.gdtrade.domain.model.market

data class MarketDataError(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val affectedSymbols: Set<String>,
    val providerId: String?
) {
    init {
        require(code.isNotBlank()) { "错误代码不能为空" }
        require(message.isNotBlank()) { "错误信息不能为空" }
        requireMarketSymbols(affectedSymbols)
        require(providerId == null || providerId.isNotBlank()) { "providerId 不能是空字符串" }
    }
}