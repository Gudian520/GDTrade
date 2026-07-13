package com.gudian.gdtrade.domain.risk

import com.gudian.gdtrade.domain.model.SignalStatus
import com.gudian.gdtrade.domain.model.StockCandidate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskEngineTest {
    private val engine = RiskEngine()

    @Test
    fun `风险否决标记优先于研究型买点`() {
        val candidate = StockCandidate(
            symbol = "000001",
            name = "测试股票",
            theme = "测试主题",
            reason = "验证风险否决优先级",
            signalStatus = SignalStatus.ResearchBuyPoint,
            riskDeniedBuy = true
        )

        val decision = engine.evaluate(candidate)

        assertFalse(decision.allowed)
    }

    @Test
    fun `研究型买点仅允许继续研究`() {
        val candidate = StockCandidate(
            symbol = "000001",
            name = "测试股票",
            theme = "测试主题",
            reason = "验证研究型买点",
            signalStatus = SignalStatus.ResearchBuyPoint,
            riskDeniedBuy = false
        )

        val decision = engine.evaluate(candidate)

        assertTrue(decision.allowed)
    }
}
