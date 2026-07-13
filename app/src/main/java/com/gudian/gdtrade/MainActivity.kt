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
        DashboardViewModel.Factory()
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
        val quoteIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("hexin://quote?code=$marketPrefix$symbol")
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        if (tryStartActivity(quoteIntent)) return

        Toast.makeText(this, "未能自动打开同花顺，请手动打开同花顺确认。", Toast.LENGTH_LONG).show()
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