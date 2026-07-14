package com.gudian.gdtrade.data.remote.market.error

import com.gudian.gdtrade.domain.model.market.MarketDataStatus

/** Remote 层可分类错误，不携带供应商原始响应或敏感内容。 */
sealed class RemoteMarketError(
    open val code: String,
    open val message: String,
    open val providerId: String?,
    open val symbol: String?
) {
    val dataStatus: MarketDataStatus = MarketDataStatus.ERROR

    data class EmptyResponse(
        override val providerId: String?
    ) : RemoteMarketError(
        code = "REMOTE_EMPTY_RESPONSE",
        message = "远程行情响应为空",
        providerId = providerId,
        symbol = null
    )

    data class MalformedResponse(
        override val providerId: String?,
        val recordNumber: Int? = null
    ) : RemoteMarketError(
        code = "REMOTE_MALFORMED_RESPONSE",
        message = if (recordNumber == null) {
            "远程行情响应格式错误"
        } else {
            "远程行情第 ${recordNumber} 条记录格式错误"
        },
        providerId = providerId,
        symbol = null
    )

    data class InvalidField(
        override val providerId: String?,
        override val symbol: String?,
        val fieldName: String,
        val reason: String
    ) : RemoteMarketError(
        code = "REMOTE_INVALID_FIELD",
        message = "远程行情字段 $fieldName 格式错误：$reason",
        providerId = providerId,
        symbol = symbol
    )

    data class HttpFailure(
        override val providerId: String?,
        val statusCode: Int
    ) : RemoteMarketError(
        code = "REMOTE_HTTP_FAILURE",
        message = "远程行情服务返回 HTTP $statusCode",
        providerId = providerId,
        symbol = null
    )

    data class NetworkFailure(
        override val providerId: String?
    ) : RemoteMarketError(
        code = "REMOTE_NETWORK_FAILURE",
        message = "远程行情网络请求失败",
        providerId = providerId,
        symbol = null
    )
}
