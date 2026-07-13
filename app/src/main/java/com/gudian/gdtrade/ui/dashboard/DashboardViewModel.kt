package com.gudian.gdtrade.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gudian.gdtrade.data.ai.AiOpinionRepository
import com.gudian.gdtrade.data.ai.ProxyGptOpinionRepository
import com.gudian.gdtrade.data.repository.LocalPreferenceRepository
import com.gudian.gdtrade.data.repository.MarketRepository
import com.gudian.gdtrade.data.repository.PortfolioRepository
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.SignalStatus
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord
import com.gudian.gdtrade.domain.model.TradeSide
import com.gudian.gdtrade.domain.risk.RiskEngine
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val marketRepository: MarketRepository,
    private val aiOpinionRepository: AiOpinionRepository = ProxyGptOpinionRepository(),
    private val riskEngine: RiskEngine = RiskEngine()
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                portfolioRepository.observeAccountGoals(),
                portfolioRepository.observePositions(),
                marketRepository.observeQuotes(emptyList()),
                marketRepository.observeCandidates(),
                portfolioRepository.observeTradeRecords()
            ) { goals, positions, quotes, candidates, records ->
                val riskCheckedCandidates = candidates.map { candidate ->
                    val decision = riskEngine.evaluate(candidate)
                    candidate.copy(riskDeniedBuy = !decision.allowed)
                }
                DashboardUiState(
                    accountGoals = goals,
                    positions = positions,
                    quotes = quotes,
                    candidates = riskCheckedCandidates,
                    tradeRecords = records,
                    isAiOpinionLoading = _uiState.value.isAiOpinionLoading,
                    aiOpinion = _uiState.value.aiOpinion
                )
            }.collect { _uiState.value = it }
        }
    }


    fun refreshMarketQuotes() {
        viewModelScope.launch { marketRepository.refreshMarketQuotes() }
    }

    fun requestAiOpinion() {
        val snapshot = _uiState.value
        _uiState.value = snapshot.copy(isAiOpinionLoading = true, aiOpinion = "GPT研究意见生成中...")
        viewModelScope.launch {
            val result = aiOpinionRepository.requestOpinion(
                positions = snapshot.positions,
                quotes = snapshot.quotes,
                candidates = snapshot.candidates,
                tradeRecords = snapshot.tradeRecords
            )
            _uiState.value = _uiState.value.copy(
                isAiOpinionLoading = false,
                aiOpinion = result.content
            )
        }
    }
    fun addPosition(symbol: String, name: String, quantityText: String, note: String) {
        val quantity = quantityText.toIntOrNull() ?: return
        viewModelScope.launch {
            portfolioRepository.addPosition(
                Position(
                    symbol = symbol.trim(),
                    name = name.trim(),
                    quantity = quantity,
                    note = note.ifBlank { "用户手动维护。" }
                )
            )
        }
    }

    fun removePosition(symbol: String) {
        viewModelScope.launch { portfolioRepository.removePosition(symbol) }
    }

    fun addCandidate(
        symbol: String,
        name: String,
        theme: String,
        reason: String,
        signalStatus: SignalStatus,
        riskDeniedBuy: Boolean
    ) {
        viewModelScope.launch {
            marketRepository.addCandidate(
                StockCandidate(
                    symbol = symbol.trim(),
                    name = name.trim(),
                    theme = theme.ifBlank { "用户观察" },
                    reason = reason.ifBlank { "用户手动加入观察池。" },
                    signalStatus = signalStatus,
                    riskDeniedBuy = riskDeniedBuy
                )
            )
        }
    }

    fun removeCandidate(symbol: String) {
        viewModelScope.launch { marketRepository.removeCandidate(symbol) }
    }

    fun addTradeRecord(
        dateText: String,
        symbol: String,
        name: String,
        side: TradeSide,
        priceText: String,
        quantityText: String,
        note: String
    ) {
        val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull() ?: return
        val price = priceText.toDoubleOrNull() ?: return
        val quantity = quantityText.toIntOrNull() ?: return
        viewModelScope.launch {
            portfolioRepository.addTradeRecord(
                TradeRecord(
                    tradeDate = date,
                    symbol = symbol.trim(),
                    name = name.trim(),
                    side = side,
                    price = price,
                    quantity = quantity,
                    note = note.ifBlank { "用户手动记录。" }
                )
            )
        }
    }

    fun removeTradeRecord(recordKey: String) {
        viewModelScope.launch { portfolioRepository.removeTradeRecord(recordKey) }
    }

    fun resetLocalData() {
        viewModelScope.launch {
            portfolioRepository.resetPortfolioData()
            marketRepository.resetMarketData()
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = LocalPreferenceRepository(context.applicationContext)
            return DashboardViewModel(
                portfolioRepository = repository,
                marketRepository = repository,
                aiOpinionRepository = ProxyGptOpinionRepository()
            ) as T
        }
    }
}
