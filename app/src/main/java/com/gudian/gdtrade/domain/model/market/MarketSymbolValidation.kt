package com.gudian.gdtrade.domain.model.market

private val marketSymbolPattern = Regex("^[0-9]{6}$")

internal fun requireMarketSymbol(symbol: String) {
    require(marketSymbolPattern.matches(symbol)) { "股票代码必须是六位数字" }
}

internal fun requireMarketSymbols(symbols: Set<String>) {
    symbols.forEach(::requireMarketSymbol)
}