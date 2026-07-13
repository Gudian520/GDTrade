package com.gudian.gdtrade.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gudian.gdtrade.data.repository.MarketRepository
import com.gudian.gdtrade.data.repository.PortfolioRepository
import com.gudian.gdtrade.data.repository.StaticMarketRepository
import com.gudian.gdtrade.data.repository.StaticPortfolioRepository
import com.gudian.gdtrade.domain.risk.RiskEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val marketRepository: MarketRepository,
    private val riskEngine: RiskEngine = RiskEngine()
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            portfolioRepository.observePositions().collect { positions ->
                val symbols = positions.map { it.symbol }
                combine(
                    portfolioRepository.observeAccountGoals(),
                    marketRepository.observeQuotes(symbols),
                    marketRepository.observeCandidates(),
                    portfolioRepository.observeTradeRecords()
                ) { goals, quotes, candidates, records ->
                    val riskCheckedCandidates = candidates.map { candidate ->
                        val decision = riskEngine.evaluate(candidate)
                        candidate.copy(riskDeniedBuy = !decision.allowed)
                    }
                    DashboardUiState(
                        accountGoals = goals,
                        positions = positions,
                        quotes = quotes,
                        candidates = riskCheckedCandidates,
                        tradeRecords = records
                    )
                }.collect { _uiState.value = it }
            }
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(
                portfolioRepository = StaticPortfolioRepository(),
                marketRepository = StaticMarketRepository()
            ) as T
        }
    }
}
