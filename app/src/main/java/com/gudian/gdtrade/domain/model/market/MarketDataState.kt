package com.gudian.gdtrade.domain.model.market

data class MarketDataState<T>(
    val status: MarketDataStatus,
    val data: T?,
    val error: MarketDataError? = null
) {
    init {
        require(status != MarketDataStatus.ERROR || error != null) {
            "Error 状态必须包含错误信息"
        }
    }
}