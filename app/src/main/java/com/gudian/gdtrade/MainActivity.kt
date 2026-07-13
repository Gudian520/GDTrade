package com.gudian.gdtrade

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
                    onOpenTongHuaShun = ::openTongHuaShun,
                    onCopyText = ::copyTextToClipboard
                )
            }
        }
    }


    private fun copyTextToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("GD Trade ChatGPT分析提示词", text))
        Toast.makeText(this, "已复制，可粘贴到 ChatGPT Plus。", Toast.LENGTH_SHORT).show()
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
