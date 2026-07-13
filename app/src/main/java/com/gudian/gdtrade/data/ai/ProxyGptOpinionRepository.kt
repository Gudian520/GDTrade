package com.gudian.gdtrade.data.ai

import com.gudian.gdtrade.BuildConfig
import com.gudian.gdtrade.domain.model.MarketQuote
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProxyGptOpinionRepository(
    private val endpoint: String = BuildConfig.GPT_ADVISOR_ENDPOINT
) : AiOpinionRepository {
    override suspend fun requestOpinion(
        positions: List<Position>,
        quotes: List<MarketQuote>,
        candidates: List<StockCandidate>,
        tradeRecords: List<TradeRecord>
    ): AiOpinionResult = withContext(Dispatchers.IO) {
        if (endpoint.isBlank()) {
            return@withContext AiOpinionResult(
                content = "GPT研究接口未配置。为避免在手机端暴露 OpenAI API Key，请先配置后端代理地址 gptAdvisorEndpoint。当前仅可使用本地风险信号和行情参考。",
                fromRemote = false
            )
        }

        runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json, text/plain")
            }
            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(buildRequestJson(positions, quotes, candidates, tradeRecords))
                }
                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (connection.responseCode !in 200..299) {
                    AiOpinionResult("GPT研究接口返回失败：${connection.responseCode}。请稍后重试。", false)
                } else {
                    AiOpinionResult(body.ifBlank { "GPT研究接口未返回内容。" }, true)
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse {
            AiOpinionResult("GPT研究接口请求失败：${it.message ?: "未知错误"}。", false)
        }
    }

    private fun buildRequestJson(
        positions: List<Position>,
        quotes: List<MarketQuote>,
        candidates: List<StockCandidate>,
        tradeRecords: List<TradeRecord>
    ): String {
        val prompt = buildString {
            appendLine("请作为A股研究辅助助手，基于以下数据给出中文研究意见。")
            appendLine("边界：不提供确定性买卖指令，不承诺收益，不自动交易；必须指出风险，风险引擎可否决买入。")
            appendLine("持仓：")
            positions.forEach { appendLine("${it.symbol} ${it.name} ${it.quantity} 股/份，备注：${it.note}") }
            appendLine("行情参考：")
            quotes.forEach { appendLine("${it.symbol} ${it.name} 价格=${it.lastPrice ?: "--"} 涨跌幅=${it.changePercent ?: "--"} 来源=${it.sourceLabel}") }
            appendLine("观察池：")
            candidates.forEach { appendLine("${it.symbol} ${it.name} 信号=${it.signalStatus.displayName} 风险否决=${it.riskDeniedBuy} 理由=${it.reason}") }
            appendLine("交易记录：")
            tradeRecords.take(10).forEach { appendLine("${it.tradeDate} ${it.name} ${it.side.displayName} ${it.quantity} 价格=${it.price}") }
            appendLine("请输出：1. 持仓风险；2. 可观察因素；3. 不建议追高的情况；4. 需要人工确认的问题。")
        }
        return "{\"prompt\":\"${prompt.escapeJson()}\"}"
    }

    private fun String.escapeJson(): String {
        return buildString {
            this@escapeJson.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }
}