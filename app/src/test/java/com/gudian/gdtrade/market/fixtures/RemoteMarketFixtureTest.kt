package com.gudian.gdtrade.market.fixtures

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteMarketFixtureTest {
    @Test
    fun `四类 Parser 输入 fixture 均可以 UTF-8 读取`() {
        val fixtureNames = listOf(
            "normal_response.json",
            "empty_response.json",
            "missing_fields.json",
            "malformed_response.txt"
        )

        fixtureNames.forEach { fixtureName ->
            assertTrue(RemoteMarketFixtureLoader.read(fixtureName).isNotBlank())
        }
        assertTrue(RemoteMarketFixtureLoader.read("normal_response.json").contains("华天科技"))
        assertFalse(RemoteMarketFixtureLoader.read("empty_response.json").contains("002185"))
    }
}
