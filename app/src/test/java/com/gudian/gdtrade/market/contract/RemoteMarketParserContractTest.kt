package com.gudian.gdtrade.market.contract

import com.gudian.gdtrade.market.fixtures.RemoteMarketFixtureLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 Parser 契约模板：不访问网络、Room 或 fallback。 */
abstract class RemoteMarketParserContractTest {
    protected abstract val parser: RemoteMarketParserContractSubject

    protected open fun readFixture(fileName: String): String {
        return RemoteMarketFixtureLoader.read(fileName)
    }

    @Test
    fun `正常响应可解析为单只行情`() {
        val result = parser.parse(readFixture("normal_response.json"))

        assertTrue(result is ParserContractResult.Success)
        val quotes = (result as ParserContractResult.Success).quotes
        assertEquals(1, quotes.size)
        assertEquals("002185", quotes.single().symbol)
        assertEquals(24.62, quotes.single().lastPrice, 0.0001)
    }

    @Test
    fun `空响应返回空数据结果`() {
        val result = parser.parse(readFixture("empty_response.json"))

        assertTrue(result is ParserContractResult.Empty)
    }

    @Test
    fun `字段缺失返回可分类错误`() {
        val result = parser.parse(readFixture("missing_fields.json"))

        assertTrue(result is ParserContractResult.Invalid)
    }

    @Test
    fun `错误格式不抛出未分类异常`() {
        val result = parser.parse(readFixture("malformed_response.txt"))

        assertTrue(result is ParserContractResult.Invalid)
    }
}

interface RemoteMarketParserContractSubject {
    fun parse(rawResponse: String): ParserContractResult
}

sealed interface ParserContractResult {
    data class Success(val quotes: List<ParsedQuoteView>) : ParserContractResult
    data object Empty : ParserContractResult
    data class Invalid(val reason: String) : ParserContractResult
}

data class ParsedQuoteView(
    val symbol: String,
    val name: String,
    val lastPrice: Double
)
