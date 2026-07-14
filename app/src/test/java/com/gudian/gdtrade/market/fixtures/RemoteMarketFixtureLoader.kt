package com.gudian.gdtrade.market.fixtures

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object RemoteMarketFixtureLoader {
    fun read(fileName: String): String {
        val relativePath = Paths.get("testFixtures", "market", "remote", fileName)
        val candidates = listOf(
            relativePath,
            Paths.get("..").resolve(relativePath),
            Paths.get(System.getProperty("user.dir")).resolve(relativePath)
        )
        val fixture = candidates.firstOrNull(Files::exists)
            ?: error("未找到远程行情 fixture：$fileName，已检查 ${candidates.joinToString()}")
        return String(Files.readAllBytes(fixture), StandardCharsets.UTF_8)
    }

    fun fixtureDirectory(): Path {
        return Paths.get("testFixtures", "market", "remote")
    }
}
