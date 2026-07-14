package com.gudian.gdtrade.domain.usecase.market

import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.SignalStatus
import com.gudian.gdtrade.domain.model.market.MarketDataStatus
import com.gudian.gdtrade.domain.risk.RiskEngine
import com.gudian.gdtrade.market.contract.MarketUseCaseContractDriver
import com.gudian.gdtrade.market.contract.MarketUseCaseContractTest
import com.gudian.gdtrade.market.contract.UseCaseContractObservation
import com.gudian.gdtrade.market.contract.UseCaseScenario
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** 使用真实行情 UseCase 与 RiskEngine 激活 QA 安全契约。 */
class RealMarketUseCaseContractTest : MarketUseCaseContractTest() {
    override val driver: MarketUseCaseContractDriver = RealMarketUseCaseContractDriver()
}

private class RealMarketUseCaseContractDriver : MarketUseCaseContractDriver {
    override fun execute(scenario: UseCaseScenario): UseCaseContractObservation = runBlocking {
        val marketDataRepository = FakeMarketDataRepository()
        val positions = if (scenario == UseCaseScenario.EMPTY_SYMBOLS) {
            emptyList()
        } else {
            listOf(Position("000001", "测试股票", 100, "契约测试持仓"))
        }
        if (positions.isNotEmpty()) {
            marketDataRepository.batchStates = listOf(
                MarketUseCaseFixtures.batchState(statusFor(scenario))
            )
        }
        val output = GetPortfolioQuotesUseCase(
            portfolioRepository = FakePortfolioRepository(positions),
            marketDataRepository = marketDataRepository
        )().first()

        val riskDecision = if (scenario == UseCaseScenario.RISK_DENIED_RESEARCH_SIGNAL) {
            RiskEngine().evaluate(
                MarketUseCaseFixtures.candidate(
                    symbol = "000001",
                    riskDeniedBuy = true,
                    signalStatus = SignalStatus.ResearchBuyPoint
                )
            )
        } else {
            null
        }
        UseCaseContractObservation(
            repositoryRequestCount = marketDataRepository.batchRequests.size,
            outputSymbols = output.orderedSymbols.toSet(),
            outputStatus = output.marketDataStatus?.name ?: output.collectionState.name,
            canGenerateTradingSignal = output.quality.supportsResearchConclusion &&
                riskDecision?.allowed != false,
            riskDenied = riskDecision?.allowed == false
        )
    }

    private fun statusFor(scenario: UseCaseScenario): MarketDataStatus {
        return when (scenario) {
            UseCaseScenario.EMPTY_SYMBOLS,
            UseCaseScenario.RISK_DENIED_RESEARCH_SIGNAL -> MarketDataStatus.SUCCESS
            UseCaseScenario.DELAYED_INPUT -> MarketDataStatus.DELAYED
            UseCaseScenario.MOCK_INPUT -> MarketDataStatus.MOCK
            UseCaseScenario.ERROR_INPUT -> MarketDataStatus.ERROR
        }
    }
}
