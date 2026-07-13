package com.gudian.gdtrade.domain.risk

import com.gudian.gdtrade.domain.model.SignalStatus
import com.gudian.gdtrade.domain.model.StockCandidate

class RiskEngine {
    fun evaluate(candidate: StockCandidate): RiskDecision {
        if (candidate.riskDeniedBuy) {
            return RiskDecision(
                allowed = false,
                status = candidate.signalStatus,
                reason = "风险引擎否决买入：当前信号只允许观察或等待。"
            )
        }

        return when (candidate.signalStatus) {
            SignalStatus.ResearchBuyPoint -> RiskDecision(
                allowed = true,
                status = candidate.signalStatus,
                reason = "出现研究型买点，但V1仍需人工确认，不自动下单。"
            )

            SignalStatus.ReduceRisk,
            SignalStatus.DoNotChase,
            SignalStatus.WaitPullback,
            SignalStatus.HighNoChase -> RiskDecision(
                allowed = false,
                status = candidate.signalStatus,
                reason = "风险状态不支持买入，等待下一次确认。"
            )

            SignalStatus.HoldWatch,
            SignalStatus.WatchOnly -> RiskDecision(
                allowed = false,
                status = candidate.signalStatus,
                reason = "当前仅用于持有或观察，不触发买入。"
            )
        }
    }
}
