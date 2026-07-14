package com.gudian.gdtrade.domain.model.market

import java.time.Instant

data class QuoteSnapshot(
    val requestedSymbols: Set<String>,
    val quotes: Map<String, StockQuote>,
    val missingSymbols: Set<String>,
    val completeness: DataCompleteness,
    val generatedAt: Instant
) {
    init {
        require(requestedSymbols.isNotEmpty()) { "行情快照必须包含请求代码" }
        requireMarketSymbols(requestedSymbols)
        requireMarketSymbols(quotes.keys)
        requireMarketSymbols(missingSymbols)
        require(quotes.keys.all { it in requestedSymbols }) { "行情结果不能包含未请求代码" }
        require(quotes.all { (symbol, quote) -> quote.symbol == symbol }) {
            "行情 Map 键必须与 StockQuote.symbol 一致"
        }
        require(missingSymbols == requestedSymbols - quotes.keys) {
            "missingSymbols 必须等于请求代码减去已返回代码"
        }
        require(quotes.keys.intersect(missingSymbols).isEmpty()) {
            "已返回代码与缺失代码不能重叠"
        }
        val expectedCompleteness = when {
            quotes.isEmpty() -> DataCompleteness.EMPTY
            missingSymbols.isEmpty() -> DataCompleteness.COMPLETE
            else -> DataCompleteness.PARTIAL
        }
        require(completeness == expectedCompleteness) { "行情完整度与快照内容不一致" }
    }
}