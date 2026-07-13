package com.gudian.gdtrade.data.local

import com.gudian.gdtrade.data.local.entity.PositionEntity
import com.gudian.gdtrade.data.local.entity.StockCandidateEntity
import com.gudian.gdtrade.data.local.entity.TradeRecordEntity
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.SignalStatus
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord
import com.gudian.gdtrade.domain.model.TradeSide
import java.time.LocalDate

internal fun Position.toEntity(displayOrder: Int): PositionEntity {
    return PositionEntity(
        symbol = symbol,
        name = name,
        quantity = quantity,
        note = note,
        displayOrder = displayOrder
    )
}

internal fun PositionEntity.toDomain(): Position {
    return Position(
        symbol = symbol,
        name = name,
        quantity = quantity,
        note = note
    )
}

internal fun StockCandidate.toEntity(displayOrder: Int): StockCandidateEntity {
    return StockCandidateEntity(
        symbol = symbol,
        name = name,
        theme = theme,
        reason = reason,
        signalStatus = signalStatus.name,
        riskDeniedBuy = riskDeniedBuy,
        displayOrder = displayOrder
    )
}

internal fun StockCandidateEntity.toDomain(): StockCandidate {
    return StockCandidate(
        symbol = symbol,
        name = name,
        theme = theme,
        reason = reason,
        signalStatus = SignalStatus.valueOf(signalStatus),
        riskDeniedBuy = riskDeniedBuy
    )
}

internal fun TradeRecord.toEntity(): TradeRecordEntity {
    return TradeRecordEntity(
        recordKey = storageKey,
        tradeDate = tradeDate.toString(),
        symbol = symbol,
        name = name,
        side = side.name,
        price = price,
        quantity = quantity,
        note = note
    )
}

internal fun TradeRecordEntity.toDomain(): TradeRecord {
    return TradeRecord(
        tradeDate = LocalDate.parse(tradeDate),
        symbol = symbol,
        name = name,
        side = TradeSide.valueOf(side),
        price = price,
        quantity = quantity,
        note = note
    )
}

internal val TradeRecord.storageKey: String
    get() = listOf(tradeDate, symbol, side, price, quantity, note).joinToString("|")
