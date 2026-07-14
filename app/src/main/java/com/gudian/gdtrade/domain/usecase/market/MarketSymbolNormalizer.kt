package com.gudian.gdtrade.domain.usecase.market

internal object MarketSymbolNormalizer {
    private val standardSymbol = Regex("^[0-9]{6}$")
    private val prefixedSymbol = Regex("^(SH|SZ)[.:]?([0-9]{6})$")

    fun normalize(rawSymbol: String): String? {
        val candidate = rawSymbol.trim().uppercase()
        if (standardSymbol.matches(candidate)) return candidate
        return prefixedSymbol.matchEntire(candidate)?.groupValues?.get(2)
    }
}
