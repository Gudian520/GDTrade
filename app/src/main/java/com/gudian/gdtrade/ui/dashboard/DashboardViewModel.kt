package com.gudian.gdtrade.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    private val riskEngine: RiskEngine = RiskEngine()
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                portfolioRepository.observePositions(),
                marketRepository.observeQuotes(emptyList()),
                marketRepository.observeCandidates(),
                portfolioRepository.observeTradeRecords()
            ) { positions, quotes, candidates, records ->
                val riskCheckedCandidates = candidates.map { candidate ->
                    val decision = riskEngine.evaluate(candidate)
                    candidate.copy(riskDeniedBuy = !decision.allowed)
                }
                DashboardUiState(
                    positions = positions,
                    quotes = quotes,
                    candidates = riskCheckedCandidates,
                    tradeRecords = records,
                    chatGptPrompt = _uiState.value.chatGptPrompt
                )
            }.collect { _uiState.value = it }
        }
    }

    fun refreshMarketQuotes() {
        viewModelScope.launch { marketRepository.refreshMarketQuotes() }
    }

    fun buildChatGptPrompt(): String {
        val snapshot = _uiState.value
        val prompt = buildString {
            appendLine("请作为A股持仓研究辅助助手，基于以下数据给出中文分析。")
            appendLine("重要边界：不提供确定性买卖指令，不承诺收益，不自动交易；请明确风险、观察条件、风险否决条件和人工确认清单。")
            appendLine()
            appendLine("一、当前持仓")
            snapshot.positions.forEach { position ->
                val quote = snapshot.quotes.firstOrNull { it.symbol == position.symbol }
                appendLine("- ${position.symbol} ${position.name}：${position.quantity} 股/份；备注：${position.note}；参考价：${quote?.lastPrice ?: "--"}；涨跌幅：${quote?.changePercent ?: "--"}；来源：${quote?.sourceLabel ?: "无"}")
            }
            appendLine()
            appendLine("二、动态观察池")
            snapshot.candidates.forEach { candidate ->
                appendLine("- ${candidate.symbol} ${candidate.name}：主题=${candidate.theme}；信号=${candidate.signalStatus.displayName}；风险否决=${candidate.riskDeniedBuy}；理由=${candidate.reason}")
            }
            appendLine()
            appendLine("三、最近交易记录")
            snapshot.tradeRecords.take(10).forEach { record ->
                appendLine("- ${record.tradeDate} ${record.name} ${record.side.displayName} ${record.quantity} 股/份，价格=${record.price}，备注=${record.note}")
            }
            appendLine()
            appendLine("请输出：")
            appendLine("1. 当前持仓风险排序；")
            appendLine("2. 值得继续跟踪的潜力股票候选，说明触发条件；")
            appendLine("3. 暂不追高或需要等待回调的标的；")
            appendLine("4. 哪些买入想法应被风险引擎否决；")
            appendLine("5. 下一次人工确认前需要检查的数据。")
        }
        _uiState.value = snapshot.copy(chatGptPrompt = prompt)
        return prompt
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
                marketRepository = repository
            ) as T
        }
    }
}
