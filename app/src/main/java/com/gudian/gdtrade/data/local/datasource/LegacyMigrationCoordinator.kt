package com.gudian.gdtrade.data.local.datasource

interface LegacyMigrationCoordinator {
    suspend fun migrateIfNeeded()
}