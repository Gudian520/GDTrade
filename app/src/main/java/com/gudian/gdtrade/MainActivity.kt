package com.gudian.gdtrade

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.gudian.gdtrade.ui.dashboard.DashboardRoute
import com.gudian.gdtrade.ui.dashboard.DashboardViewModel
import com.gudian.gdtrade.ui.theme.GDTradeTheme

class MainActivity : ComponentActivity() {
    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GDTradeTheme {
                DashboardRoute(
                    viewModel = viewModel,
                    onOpenTongHuaShun = ::openTongHuaShun
                )
            }
        }
    }

    private fun openTongHuaShun(symbol: String) {
        val marketPrefix = when {
            symbol.startsWith("6") -> "sh"
            else -> "sz"
        }
        val quoteCode = "$marketPrefix$symbol"
        val quoteUris = listOf(
            "hexin://quote?code=$quoteCode",
            "hexin://stock?code=$quoteCode",
            "hexin://detail?code=$quoteCode"
        )

        quoteUris.forEach { uri ->
            val quoteIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            if (tryStartActivity(quoteIntent)) return
        }

        if (tryOpenInstalledTongHuaShun()) return

        Toast.makeText(this, "未能自动打开同花顺，请手动打开同花顺确认。", Toast.LENGTH_LONG).show()
    }

    private fun tryOpenInstalledTongHuaShun(): Boolean {
        val packageNames = listOf(
            "com.hexin.plat.android",
            "com.hexin.android",
            "com.hexin.stock"
        )
        packageNames.forEach { packageName ->
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null && tryStartActivity(launchIntent)) return true
        }
        return false
    }

    private fun tryStartActivity(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
