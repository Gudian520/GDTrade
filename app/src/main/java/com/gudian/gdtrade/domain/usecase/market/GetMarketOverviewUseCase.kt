package com.gudian.gdtrade.domain.usecase.market

/**
 * 当前基础报价端口只覆盖显式股票集合，不能据此推断全市场、板块或资金流。
 */
class GetMarketOverviewUseCase {
    operator fun invoke(): MarketOverviewResult {
        return MarketOverviewResult.InsufficientData(
            missingCapabilities = setOf(
                MarketOverviewCapability.FULL_MARKET_COVERAGE,
                MarketOverviewCapability.SECTOR_DATA,
                MarketOverviewCapability.CAPITAL_FLOW_DATA
            ),
            reason = "当前仅有显式股票基础报价，缺少全市场、板块和资金流数据，无法生成市场整体结论。"
        )
    }
}
