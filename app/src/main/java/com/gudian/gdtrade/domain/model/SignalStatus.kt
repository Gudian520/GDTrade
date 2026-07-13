package com.gudian.gdtrade.domain.model

enum class SignalStatus(val displayName: String) {
    HoldWatch("持有观察"),
    ReduceRisk("减仓风险提示"),
    DoNotChase("暂不追高"),
    WaitPullback("等待回调"),
    ResearchBuyPoint("出现研究型买点"),
    HighNoChase("高位不追"),
    WatchOnly("仅观察")
}
