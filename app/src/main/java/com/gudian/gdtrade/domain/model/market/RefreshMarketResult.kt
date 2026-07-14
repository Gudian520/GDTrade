package com.gudian.gdtrade.domain.model.market

data class RefreshMarketResult(
    val requestedSymbols: Set<String>,
    val refreshedSymbols: Set<String>,
    val failedSymbols: Set<String>,
    val status: MarketDataStatus,
    val error: MarketDataError? = null
) {
    init {
        require(requestedSymbols.isNotEmpty()) { "刷新请求不能为空" }
        requireMarketSymbols(requestedSymbols)
        requireMarketSymbols(refreshedSymbols)
        requireMarketSymbols(failedSymbols)
        require(refreshedSymbols.all { it in requestedSymbols }) { "刷新结果包含未请求代码" }
        require(failedSymbols.all { it in requestedSymbols }) { "失败结果包含未请求代码" }
        require(refreshedSymbols.intersect(failedSymbols).isEmpty()) {
            "刷新成功代码与失败代码不能重叠"
        }
        require(status != MarketDataStatus.ERROR || error != null) {
            "Error 刷新结果必须包含错误信息"
        }
    }
}