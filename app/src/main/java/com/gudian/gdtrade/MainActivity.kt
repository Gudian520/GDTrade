package com.gudian.gdtrade

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("hexin://quote?code=$marketPrefix$symbol")
            setPackage("com.hexin.plat.android")
        }
        runCatching { startActivity(intent) }
            .onFailure {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=com.hexin.plat.android")
                    )
                )
            }
    }
}
