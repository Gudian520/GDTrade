package com.gudian.gdtrade.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gudian.gdtrade.domain.model.AccountGoal
import com.gudian.gdtrade.domain.model.MarketQuote
import com.gudian.gdtrade.domain.model.Position
import com.gudian.gdtrade.domain.model.SignalStatus
import com.gudian.gdtrade.domain.model.StockCandidate
import com.gudian.gdtrade.domain.model.TradeRecord
import com.gudian.gdtrade.domain.model.TradeSide
import com.gudian.gdtrade.ui.theme.GDTradeTheme
import java.time.LocalDate

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    onOpenTongHuaShun: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    DashboardScreen(
        uiState = uiState,
        onOpenTongHuaShun = onOpenTongHuaShun,
        onAddPosition = viewModel::addPosition,
        onRemovePosition = viewModel::removePosition,
        onAddCandidate = viewModel::addCandidate,
        onRemoveCandidate = viewModel::removeCandidate,
        onAddTradeRecord = viewModel::addTradeRecord,
        onRemoveTradeRecord = viewModel::removeTradeRecord,
        onResetLocalData = viewModel::resetLocalData
    )
}

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onOpenTongHuaShun: (String) -> Unit,
    onAddPosition: (String, String, String, String) -> Unit,
    onRemovePosition: (String) -> Unit,
    onAddCandidate: (String, String, String, String, SignalStatus, Boolean) -> Unit,
    onRemoveCandidate: (String) -> Unit,
    onAddTradeRecord: (String, String, String, TradeSide, String, String, String) -> Unit,
    onRemoveTradeRecord: (String) -> Unit,
    onResetLocalData: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Header(uiState.disclosure)
        }
        item { GoalSection(uiState.accountGoals) }
        item {
            LocalDataEditor(
                onAddPosition = onAddPosition,
                onAddCandidate = onAddCandidate,
                onAddTradeRecord = onAddTradeRecord,
                onResetLocalData = onResetLocalData
            )
        }
        item { SectionTitle("当前持仓") }
        items(uiState.positions, key = { "position-${it.symbol}" }) { position ->
            val quote = uiState.quotes.firstOrNull { it.symbol == position.symbol }
            PositionCard(position, quote, onOpenTongHuaShun, onRemovePosition)
        }
        item { SectionTitle("动态观察池") }
        items(uiState.candidates, key = { "candidate-${it.symbol}" }) { candidate ->
            CandidateCard(candidate, onOpenTongHuaShun, onRemoveCandidate)
        }
        item { SectionTitle("交易记录") }
        items(uiState.tradeRecords, key = { "trade-${it.localUiKey}" }) { record ->
            TradeRecordCard(record, onRemoveTradeRecord)
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun Header(disclosure: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "策略驾驶舱",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = disclosure,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun GoalSection(goals: List<AccountGoal>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle("账户阶段目标")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                goals.forEach { goal ->
                    AssistChip(onClick = {}, label = { Text("${goal.amount}") })
                }
            }
        }
    }
}

@Composable
private fun LocalDataEditor(
    onAddPosition: (String, String, String, String) -> Unit,
    onAddCandidate: (String, String, String, String, SignalStatus, Boolean) -> Unit,
    onAddTradeRecord: (String, String, String, TradeSide, String, String, String) -> Unit,
    onResetLocalData: () -> Unit
) {
    var positionSymbol by remember { mutableStateOf("") }
    var positionName by remember { mutableStateOf("") }
    var positionQuantity by remember { mutableStateOf("") }
    var positionNote by remember { mutableStateOf("") }

    var candidateSymbol by remember { mutableStateOf("") }
    var candidateName by remember { mutableStateOf("") }
    var candidateTheme by remember { mutableStateOf("") }
    var candidateReason by remember { mutableStateOf("") }
    var candidateSignal by remember { mutableStateOf(SignalStatus.WatchOnly) }
    var candidateDenied by remember { mutableStateOf(false) }

    var tradeDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var tradeSymbol by remember { mutableStateOf("") }
    var tradeName by remember { mutableStateOf("") }
    var tradeSide by remember { mutableStateOf(TradeSide.Buy) }
    var tradePrice by remember { mutableStateOf("") }
    var tradeQuantity by remember { mutableStateOf("") }
    var tradeNote by remember { mutableStateOf("") }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("本地数据维护")
                TextButton(onClick = onResetLocalData) { Text("恢复样例") }
            }

            SectionTitle("新增持仓")
            InputRow("代码", positionSymbol, { positionSymbol = it }, "名称", positionName, { positionName = it })
            InputRow("数量", positionQuantity, { positionQuantity = it }, "备注", positionNote, { positionNote = it })
            Button(
                onClick = {
                    onAddPosition(positionSymbol, positionName, positionQuantity, positionNote)
                    positionSymbol = ""
                    positionName = ""
                    positionQuantity = ""
                    positionNote = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存持仓") }

            SectionTitle("新增观察")
            InputRow("代码", candidateSymbol, { candidateSymbol = it }, "名称", candidateName, { candidateName = it })
            InputRow("主题", candidateTheme, { candidateTheme = it }, "理由", candidateReason, { candidateReason = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { candidateSignal = candidateSignal.nextSignal() },
                    modifier = Modifier.weight(1f)
                ) { Text(candidateSignal.displayName) }
                Spacer(modifier = Modifier.width(8.dp))
                Checkbox(checked = candidateDenied, onCheckedChange = { candidateDenied = it })
                Text("风险否决")
            }
            Button(
                onClick = {
                    onAddCandidate(candidateSymbol, candidateName, candidateTheme, candidateReason, candidateSignal, candidateDenied)
                    candidateSymbol = ""
                    candidateName = ""
                    candidateTheme = ""
                    candidateReason = ""
                    candidateSignal = SignalStatus.WatchOnly
                    candidateDenied = false
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存观察") }

            SectionTitle("新增交易")
            InputRow("日期", tradeDate, { tradeDate = it }, "代码", tradeSymbol, { tradeSymbol = it })
            InputRow("名称", tradeName, { tradeName = it }, "价格", tradePrice, { tradePrice = it })
            InputRow("数量", tradeQuantity, { tradeQuantity = it }, "备注", tradeNote, { tradeNote = it })
            OutlinedButton(onClick = { tradeSide = if (tradeSide == TradeSide.Buy) TradeSide.Sell else TradeSide.Buy }) {
                Text("方向：${tradeSide.displayName}")
            }
            Button(
                onClick = {
                    onAddTradeRecord(tradeDate, tradeSymbol, tradeName, tradeSide, tradePrice, tradeQuantity, tradeNote)
                    tradeSymbol = ""
                    tradeName = ""
                    tradePrice = ""
                    tradeQuantity = ""
                    tradeNote = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存交易") }
        }
    }
}

@Composable
private fun InputRow(
    firstLabel: String,
    firstValue: String,
    onFirstChange: (String) -> Unit,
    secondLabel: String,
    secondValue: String,
    onSecondChange: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = firstValue,
            onValueChange = onFirstChange,
            label = { Text(firstLabel) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = secondValue,
            onValueChange = onSecondChange,
            label = { Text(secondLabel) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PositionCard(
    position: Position,
    quote: MarketQuote?,
    onOpenTongHuaShun: (String) -> Unit,
    onRemovePosition: (String) -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CardHeader(position.name, position.symbol)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${position.quantity} 股/份",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = quote?.quoteDisplay() ?: "暂无行情数据",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(position.note, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = quote?.sourceLabel ?: "未接入行情源",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenTongHuaShun(position.symbol) }) { Text("打开同花顺人工确认") }
                TextButton(onClick = { onRemovePosition(position.symbol) }) { Text("删除") }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: StockCandidate,
    onOpenTongHuaShun: (String) -> Unit,
    onRemoveCandidate: (String) -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CardHeader(candidate.name, candidate.symbol)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SignalBadge(candidate.signalStatus)
                Text(
                    text = if (candidate.riskDeniedBuy) "风险否决买入" else "允许继续研究",
                    color = if (candidate.riskDeniedBuy) Color(0xFFB3261E) else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text("主题：${candidate.theme}", style = MaterialTheme.typography.bodyMedium)
            Text(candidate.reason, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenTongHuaShun(candidate.symbol) }) { Text("打开同花顺人工确认") }
                TextButton(onClick = { onRemoveCandidate(candidate.symbol) }) { Text("删除") }
            }
        }
    }
}

@Composable
private fun TradeRecordCard(
    record: TradeRecord,
    onRemoveTradeRecord: (String) -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${record.name} ${record.symbol}", fontWeight = FontWeight.SemiBold)
                Text("${record.tradeDate} ${record.side.displayName} ${record.quantity} 股/份")
                Text(record.note, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%.2f".format(record.price),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { onRemoveTradeRecord(record.localUiKey) }) { Text("删除") }
            }
        }
    }
}

@Composable
private fun CardHeader(name: String, symbol: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(symbol, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
    }
}

@Composable
private fun SignalBadge(status: SignalStatus) {
    Surface(
        color = when (status) {
            SignalStatus.ReduceRisk,
            SignalStatus.DoNotChase,
            SignalStatus.HighNoChase -> Color(0xFFFFE7E3)
            SignalStatus.WaitPullback -> Color(0xFFFFF1D6)
            SignalStatus.ResearchBuyPoint -> Color(0xFFDFF3EA)
            SignalStatus.HoldWatch,
            SignalStatus.WatchOnly -> Color(0xFFE8EEF8)
        },
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = status.displayName,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

private fun SignalStatus.nextSignal(): SignalStatus {
    val values = SignalStatus.values()
    return values[(values.indexOf(this) + 1) % values.size]
}

private val TradeRecord.localUiKey: String
    get() = listOf(tradeDate, symbol, side, price, quantity, note).joinToString("|")


private fun MarketQuote.quoteDisplay(): String {
    val priceText = lastPrice?.let { "%.3f".format(it) } ?: "--"
    val changeText = changePercent?.let { "%.2f%%".format(it) } ?: "--"
    return "参考价 $priceText / 涨跌幅 $changeText"
}
@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    GDTradeTheme {
        DashboardScreen(
            uiState = DashboardUiState(
                accountGoals = listOf(11500, 12500, 13800, 15000).map { AccountGoal(it, false) },
                positions = StaticPortfolioRepositoryPreview.positions,
                quotes = StaticPortfolioRepositoryPreview.quotes,
                candidates = StaticPortfolioRepositoryPreview.candidates,
                tradeRecords = StaticPortfolioRepositoryPreview.records
            ),
            onOpenTongHuaShun = {},
            onAddPosition = { _, _, _, _ -> },
            onRemovePosition = {},
            onAddCandidate = { _, _, _, _, _, _ -> },
            onRemoveCandidate = {},
            onAddTradeRecord = { _, _, _, _, _, _, _ -> },
            onRemoveTradeRecord = {},
            onResetLocalData = {}
        )
    }
}

private object StaticPortfolioRepositoryPreview {
    val positions = listOf(
        Position("002185", "华天科技", 200, "当前持仓，V1只做监控与提醒。"),
        Position("515070", "华夏中证人工智能主题ETF", 800, "当前持仓，跟踪主题风险。"),
        Position("000725", "京东方A", 100, "当前持仓，减仓后观察。")
    )
    val quotes = listOf(
        MarketQuote("002185", "华天科技", 24.62, null, "V1静态样例，非实时行情", false)
    )
    val candidates = listOf(
        StockCandidate("002185", "华天科技", "半导体封测", "持仓观察。", SignalStatus.HoldWatch, false)
    )
    val records = listOf(
        TradeRecord(LocalDate.of(2026, 7, 13), "002185", "华天科技", TradeSide.Sell, 24.62, 100, "已知交易记录")
    )
}
