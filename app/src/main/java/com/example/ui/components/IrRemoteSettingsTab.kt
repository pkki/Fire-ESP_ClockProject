package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ClockViewModel
import com.example.model.EspSensorData
import com.example.model.IrDeviceCategory
import com.example.model.IrLearnState
import com.example.model.IrRemoteButton

@Composable
fun IrRemoteSettingsTab(
    viewModel: ClockViewModel,
    espSensorData: EspSensorData,
    irButtons: List<IrRemoteButton>,
    irLearnState: IrLearnState,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingButton by remember { mutableStateOf<IrRemoteButton?>(null) }
    var showCircuitDialog by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf<IrDeviceCategory?>(null) }

    // 学習で新しい信号が受信されたら自動で追加ダイアログを開く
    LaunchedEffect(irLearnState.lastLearnedSignal) {
        if (irLearnState.lastLearnedSignal != null) {
            editingButton = irLearnState.lastLearnedSignal
            showAddDialog = true
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. ESP32-C3 接続ステータス ＆ 学習クイックパネル
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (espSensorData.isConnected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (espSensorData.isConnected) Color(0xFF10B981) else Color(0xFFEF4444)
                                    )
                            )
                            Column {
                                Text(
                                    text = if (espSensorData.isConnected) "ESP32-C3 赤外線ユニット接続中 (BLE)" else "ESP32-C3 未接続",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (espSensorData.isConnected) "双方向赤外線送受信・学習機能が利用可能です" else "設定の「ESP温湿度気圧」タブからBLE接続を確認してください",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showCircuitDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("2SC1815 回路図", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(14.dp))

                    // 学習モード操作部
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (irLearnState.isLearning) {
                            Button(
                                onClick = { viewModel.stopIrLearning() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("学習を停止")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startIrLearning() },
                                enabled = espSensorData.isConnected,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Sensors, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("リモコン信号を学習 (受信待機)")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                editingButton = IrRemoteButton(
                                    name = "新規リモコン",
                                    category = IrDeviceCategory.LIGHTING
                                )
                                showAddDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("手動コード登録")
                        }
                    }

                    if (irLearnState.statusMessage != null || irLearnState.isLearning) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (irLearnState.isLearning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = if (irLearnState.isLearning) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (irLearnState.isLearning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = irLearnState.statusMessage ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (irLearnState.isLearning) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // リアルタイム生データモニター表示 (ESP32から受信した直近のログ)
                                if (irLearnState.latestRawLog != null) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF0F172A),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "ESP生受信: ${irLearnState.latestRawLog}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = Color(0xFF38BDF8),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                // 学習完了カード（ダイアログ以外に画面上でも直接保存・登録可能）
                                if (irLearnState.lastLearnedSignal != null) {
                                    val signal = irLearnState.lastLearnedSignal
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "🎉 受信成功: ${signal.protocol} / ${signal.hexCode} (${signal.bits}bit)",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                if (signal.rawCode.isNotBlank()) {
                                                    Text(
                                                        text = "RAWパルス: ${signal.rawCode.take(30)}...",
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    editingButton = signal
                                                    showAddDialog = true
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Text("ボタン登録", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. カテゴリフィルター
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryFilter == null,
                        onClick = { selectedCategoryFilter = null },
                        label = { Text("すべて (${irButtons.size})") },
                        leadingIcon = {
                            Icon(Icons.Default.AllInclusive, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
                items(IrDeviceCategory.values()) { cat ->
                    val count = irButtons.count { it.category == cat }
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text("${cat.displayName.split("・")[0]} ($count)") },
                        leadingIcon = {
                            Icon(getCategoryIcon(cat), contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
        }

        // 3. リモコンボタン一覧
        val filteredButtons = if (selectedCategoryFilter == null) {
            irButtons
        } else {
            irButtons.filter { it.category == selectedCategoryFilter }
        }

        if (filteredButtons.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Sensors,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "登録されたリモコンボタンがありません",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "「リモコン信号を学習」を押して、実物のリモコンをESP32に向けてボタンを押してください",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            items(filteredButtons, key = { it.id }) { button ->
                IrButtonCard(
                    button = button,
                    isConnected = espSensorData.isConnected,
                    onSend = { viewModel.sendIrButton(button) },
                    onEdit = {
                        editingButton = button
                        showAddDialog = true
                    },
                    onDelete = { viewModel.deleteIrButton(button.id) }
                )
            }
        }
    }

    // 編集・追加ダイアログ
    if (showAddDialog && editingButton != null) {
        IrButtonEditDialog(
            initialButton = editingButton!!,
            onDismiss = {
                showAddDialog = false
                editingButton = null
                viewModel.clearIrLearnedSignal()
            },
            onSave = { updated ->
                viewModel.saveIrButton(updated)
                showAddDialog = false
                editingButton = null
                viewModel.clearIrLearnedSignal()
            },
            onTestSend = { btn ->
                viewModel.sendIrButton(btn)
            }
        )
    }

    // 回路図・配線解説ダイアログ
    if (showCircuitDialog) {
        IrCircuitGuideDialog(onDismiss = { showCircuitDialog = false })
    }
}

@Composable
fun IrButtonCard(
    button: IrRemoteButton,
    isConnected: Boolean,
    onSend: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var isSending by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(parseHexColor(button.colorHex).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            getCategoryIcon(button.category),
                            contentDescription = null,
                            tint = parseHexColor(button.colorHex),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = button.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = button.category.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "${button.protocol} (${button.hexCode})",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (button.rawCode.isNotBlank()) {
                                val pulseCount = button.rawCode.split(",").filter { it.isNotBlank() }.size
                                Text(
                                    text = "• RAW ${pulseCount}P",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (button.repeatCount > 1) {
                                Text(
                                    text = "• x${button.repeatCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 送信ボタン
                Button(
                    onClick = {
                        isSending = true
                        onSend()
                    },
                    enabled = isConnected,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = parseHexColor(button.colorHex)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("発信", fontWeight = FontWeight.Bold)
                }
            }

            // 自動トリガー・アラーム連携タグ
            if (button.triggerOnAlarm || button.triggerOnNightMode || button.triggerOnNightExit || button.isScheduleEnabled) {
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (button.triggerOnAlarm) {
                        AssistChip(
                            onClick = {},
                            label = { Text("⏰ アラーム時自動送信", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                    if (button.triggerOnNightMode) {
                        AssistChip(
                            onClick = {},
                            label = { Text("🌙 夜間突入時(消灯)", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                    if (button.triggerOnNightExit) {
                        AssistChip(
                            onClick = {},
                            label = { Text("☀️ 夜間解除時(点灯)", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                    if (button.isScheduleEnabled) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    "⏱ 毎日 %02d:%02d".format(button.scheduleHour, button.scheduleMinute),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }
            }

            // 編集 / 削除 アクション
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "編集", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrButtonEditDialog(
    initialButton: IrRemoteButton,
    onDismiss: () -> Unit,
    onSave: (IrRemoteButton) -> Unit,
    onTestSend: (IrRemoteButton) -> Unit
) {
    var name by remember { mutableStateOf(initialButton.name) }
    var category by remember { mutableStateOf(initialButton.category) }
    var protocol by remember { mutableStateOf(initialButton.protocol) }
    var hexCode by remember { mutableStateOf(initialButton.hexCode) }
    var bits by remember { mutableStateOf(initialButton.bits.toString()) }
    var rawCode by remember { mutableStateOf(initialButton.rawCode) }
    var colorHex by remember { mutableStateOf(initialButton.colorHex) }

    var triggerOnAlarm by remember { mutableStateOf(initialButton.triggerOnAlarm) }
    var triggerOnNightMode by remember { mutableStateOf(initialButton.triggerOnNightMode) }
    var triggerOnNightExit by remember { mutableStateOf(initialButton.triggerOnNightExit) }

    var repeatCount by remember { mutableStateOf(initialButton.repeatCount) }

    var isScheduleEnabled by remember { mutableStateOf(initialButton.isScheduleEnabled) }
    var scheduleHour by remember { mutableStateOf(initialButton.scheduleHour) }
    var scheduleMinute by remember { mutableStateOf(initialButton.scheduleMinute) }

    val colorOptions = listOf("#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#64748B")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("リモコンボタン設定")
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 名前
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("ボタン名 (例: リビング照明 点灯)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 家電種別 (カテゴリ)
                item {
                    Text("家電カテゴリ", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(IrDeviceCategory.values()) { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat.displayName.split("・")[0]) }
                            )
                        }
                    }
                }

                // 赤外線コード詳細
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("赤外線コード詳細 (学習済み / 手動)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = protocol,
                                    onValueChange = { protocol = it },
                                    label = { Text("プロトコル") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = bits,
                                    onValueChange = { bits = it },
                                    label = { Text("Bits") },
                                    modifier = Modifier.weight(0.6f),
                                    singleLine = true
                                )
                            }
                            OutlinedTextField(
                                value = hexCode,
                                onValueChange = { hexCode = it },
                                label = { Text("HEXコード (例: 0x00FF18E7)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
                            )
                            if (rawCode.isNotBlank()) {
                                val pulseCount = rawCode.split(",").filter { it.isNotBlank() }.size
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "高精度RAWパルス記録済み ($pulseCount パルス)",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = "照明の調光ボタン（暗く/明るく）などの長押しや未知フォーマットも、このパルス列により実機リモコンと100%同じタイミングで正確に発信されます。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 送信リピート回数 (長押し・連続調光)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("送信リピート回数 (長押し/連続調光)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("明るさを下げる/上げるなど、連続で効かせたい場合に増やします", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = "${repeatCount}回 送信",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(1 to "通常(1回)", 2 to "2回", 3 to "3回(調光)", 5 to "5回(長押し相当)").forEach { (cnt, label) ->
                                FilterChip(
                                    selected = repeatCount == cnt,
                                    onClick = { repeatCount = cnt },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                // ボタンカラー
                item {
                    Text("ボタン表示色", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        colorOptions.forEach { hex ->
                            val isSelected = colorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(hex))
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { colorHex = hex }
                            )
                        }
                    }
                }

                // 自動トリガー・アラーム連携
                item {
                    Text("自動実行・アラーム連携", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("アラーム鳴動時に自動送信", style = MaterialTheme.typography.bodyMedium)
                            Text("朝の目覚ましアラームと同時に部屋の電気や暖房をON", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = triggerOnAlarm, onCheckedChange = { triggerOnAlarm = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("常夜灯(ナイトスタンド)ON時に送信", style = MaterialTheme.typography.bodyMedium)
                            Text("就寝時に照明を自動消灯", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = triggerOnNightMode, onCheckedChange = { triggerOnNightMode = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("常夜灯解除(朝の復帰)時に送信", style = MaterialTheme.typography.bodyMedium)
                            Text("起床タップ時に照明を自動点灯", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = triggerOnNightExit, onCheckedChange = { triggerOnNightExit = it })
                    }
                }

                // 指定時刻スケジュール
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("指定時刻に毎日自動送信", style = MaterialTheme.typography.bodyMedium)
                            Text("決まった時間にエアコンや照明をタイマー制御", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isScheduleEnabled, onCheckedChange = { isScheduleEnabled = it })
                    }

                    AnimatedVisibility(visible = isScheduleEnabled) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = scheduleHour.toString(),
                                onValueChange = { scheduleHour = (it.toIntOrNull() ?: 0).coerceIn(0, 23) },
                                label = { Text("時 (0-23)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = scheduleMinute.toString(),
                                onValueChange = { scheduleMinute = (it.toIntOrNull() ?: 0).coerceIn(0, 59) },
                                label = { Text("分 (0-59)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = initialButton.copy(
                        name = name.trim().ifEmpty { "リモコンボタン" },
                        category = category,
                        protocol = protocol.trim().ifEmpty { "NEC" },
                        hexCode = hexCode.trim(),
                        bits = bits.toIntOrNull() ?: 32,
                        rawCode = rawCode.trim(),
                        colorHex = colorHex,
                        triggerOnAlarm = triggerOnAlarm,
                        triggerOnNightMode = triggerOnNightMode,
                        triggerOnNightExit = triggerOnNightExit,
                        repeatCount = repeatCount,
                        isScheduleEnabled = isScheduleEnabled,
                        scheduleHour = scheduleHour,
                        scheduleMinute = scheduleMinute
                    )
                    onSave(updated)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val testBtn = initialButton.copy(
                            protocol = protocol,
                            hexCode = hexCode,
                            bits = bits.toIntOrNull() ?: 32,
                            rawCode = rawCode,
                            repeatCount = repeatCount
                        )
                        onTestSend(testBtn)
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("テスト発信")
                }
                TextButton(onClick = onDismiss) {
                    Text("キャンセル")
                }
            }
        }
    )
}

@Composable
fun IrCircuitGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ElectricalServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("2SC1815 赤外線LED駆動 回路図")
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "ESP32-C3のGPIO出力だけでは赤外線LEDを強力に光らせるのが難しいため、2SC1815（NPNトランジスタ）をスイッチとして使用します。部屋の隅やエアコンまで確実に届くようになります。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = """
【2SC1815 赤外線LED ドライブ回路】

  +3.3V (または USB 5V/VBUS)
      |
     [R2: 電流制限抵抗 (33Ω〜100Ω)]
      |
    ( + ) アノード (長い足)
   [赤外線LED]
    ( - ) カソード (短い足)
      |
      +---------> [ Collector (C) ]
                          |
  [GPIO 3] ----[R1: 470Ω〜1kΩ]----> [ Base (B) ] 2SC1815 (NPN)
                                           |
  [GND] ---------------------------> [ Emitter (E) ]

★ 2SC1815 ピン配置 (平らな印字面を手前に向けて足を下にした時)
   左から:  [ 1: E (エミッタ) ]  [ 2: C (コレクタ) ]  [ 3: B (ベース) ]
                            """.trimIndent(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                item {
                    Text("【抵抗値の目安】", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text("• R1 (ベース抵抗): 470Ω 〜 1kΩ (ESP32のGPIO 3を保護しつつトランジスタを飽和ON)", style = MaterialTheme.typography.bodySmall)
                    Text("• R2 (LED制限抵抗): 3.3V電源時 = 33Ω〜47Ω (約20mA) / 5V電源時 = 68Ω〜100Ω (約35〜50mA)", style = MaterialTheme.typography.bodySmall)
                }

                item {
                    Text("【赤外線受信モジュール配線】", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text("• VCC -> 3.3V (または 5V)\n• GND -> GND\n• OUT -> ESP32-C3 GPIO 2", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

fun getCategoryIcon(cat: IrDeviceCategory): ImageVector {
    return when (cat) {
        IrDeviceCategory.LIGHTING -> Icons.Default.Lightbulb
        IrDeviceCategory.AIR_CONDITIONER -> Icons.Default.AcUnit
        IrDeviceCategory.TV -> Icons.Default.Tv
        IrDeviceCategory.FAN -> Icons.Default.Air
        IrDeviceCategory.HEATER -> Icons.Default.LocalFireDepartment
        IrDeviceCategory.OTHER -> Icons.Default.Sensors
    }
}

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFF3B82F6)
    }
}
