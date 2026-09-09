package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.JapanMunicipalities
import com.example.data.MunicipalityItem
import com.example.model.ClockPreferencesState
import com.example.model.WeatherState

private val REGIONS = listOf(
    "全47都道府県" to JapanMunicipalities.ALL_47_PREFECTURES,
    "関東" to listOf("東京都", "神奈川県", "埼玉県", "千葉県", "茨城県", "栃木県", "群馬県"),
    "近畿" to listOf("大阪府", "京都府", "兵庫県", "奈良県", "滋賀県", "和歌山県"),
    "中部" to listOf("愛知県", "静岡県", "岐阜県", "三重県", "新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県"),
    "北海道・東北" to listOf("北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県"),
    "中国・四国" to listOf("広島県", "岡山県", "山口県", "鳥取県", "島根県", "香川県", "徳島県", "愛媛県", "高知県"),
    "九州・沖縄" to listOf("福岡県", "佐賀県", "長崎県", "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherAndWarningDialog(
    preferences: ClockPreferencesState,
    weather: WeatherState,
    searchResults: List<MunicipalityItem>,
    isSearching: Boolean,
    isDetectingLocation: Boolean,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSelectMunicipality: (MunicipalityItem) -> Unit,
    onSelectPrefecture: (String) -> Unit,
    onDetectLocation: (onResult: (Boolean, String) -> Unit) -> Unit,
    onToggleWeather: () -> Unit,
    onToggleWarnings: () -> Unit,
    onToggleDemoWarnings: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = preferences.colorPalette.primary

    var searchQuery by remember { mutableStateOf("") }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var selectedRegionIndex by remember { mutableIntStateOf(0) }

    // Permission launcher for location
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            locationMessage = "位置情報を測定中..."
            onDetectLocation { success, msg ->
                locationMessage = msg
            }
        } else {
            locationMessage = "位置情報の利用が許可されませんでした"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF14141A))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .padding(24.dp)
                .testTag("dialog_weather_and_warning")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFF7DF1E),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "地域・全国市町村 & 気象警報設定",
                                color = Color(0xFFEEEEF2),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "現在設定: ${preferences.selectedCityName} (${preferences.selectedPrefecture})",
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "閉じる",
                            tint = Color(0xFF9E9EA8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // GPS Auto Location Button
                Button(
                    onClick = {
                        val hasFine = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasFine || hasCoarse) {
                            locationMessage = "現在地を自動取得しています..."
                            onDetectLocation { success, msg ->
                                locationMessage = msg
                            }
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x2E00E5FF),
                        contentColor = Color(0xFF00E5FF)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isDetectingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF00E5FF),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDetectingLocation) "現在地を取得中..." else "現在地から自動設定 (GPS / Wi-Fi)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (locationMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = locationMessage ?: "",
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search any municipality in Japan
                Text(
                    text = "全国の市町村を検索 (全1,700以上の市区町村に対応)",
                    color = Color(0xFFD4D4DE),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        onSearch(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("例: 新宿区, 横浜市, つくば市, 別府市, 札幌市...", color = Color(0xFF6E6E78), fontSize = 13.sp)
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFF888894))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                onClearSearch()
                            }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "クリア", tint = Color(0xFF888894))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = Color(0xFFEEEEF2),
                        unfocusedTextColor = Color(0xFFEEEEF2),
                        focusedContainerColor = Color(0x18FFFFFF),
                        unfocusedContainerColor = Color(0x12FFFFFF)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(searchQuery) })
                )

                // Search results list
                if (isSearching) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = accentColor, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("全国データベースを検索中...", color = Color(0xFF9E9EA8), fontSize = 12.sp)
                    }
                } else if (searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("検索結果 (${searchResults.size}件):", color = Color(0xFF9E9EA8), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x18FFFFFF))
                            .padding(6.dp)
                    ) {
                        searchResults.forEach { item ->
                            val isSelected = preferences.selectedCityName == item.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        onSelectMunicipality(item)
                                        searchQuery = ""
                                        onClearSearch()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = if (isSelected) accentColor else Color(0xFF888894),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.fullName,
                                        color = if (isSelected) accentColor else Color(0xFFEEEEF2),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (isSelected) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Currently active warnings status for selected region
                Text(
                    text = "気象庁発表の警報・注意報 (${preferences.selectedCityName})",
                    color = Color(0xFFD4D4DE),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (weather.warnings.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x18FFFFFF))
                            .padding(12.dp)
                    ) {
                        weather.warnings.forEach { warning ->
                            WarningPill(warning = warning)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x14FFFFFF))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "現在、発表中の警報・注意報はありません (平常)",
                            color = Color(0xFF888894),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 47 Prefectures Quick Picker
                Text(
                    text = "都道府県から選択 (全47都道府県)",
                    color = Color(0xFFD4D4DE),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Region Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    REGIONS.forEachIndexed { index, (label, _) ->
                        val isSelected = selectedRegionIndex == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) accentColor.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { selectedRegionIndex = index }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) accentColor else Color(0xFF888894),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Prefectures grid
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val currentList = REGIONS[selectedRegionIndex].second
                    currentList.forEach { pref ->
                        val isSelected = preferences.selectedPrefecture == pref
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color(0x18FFFFFF))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else Color(0x22FFFFFF),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectPrefecture(pref) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = pref,
                                color = if (isSelected) accentColor else Color(0xFFE2E2EA),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Toggles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("気象警報・注意報の常時表示", color = Color(0xFFEEEEF2), fontSize = 13.sp)
                    Switch(
                        checked = preferences.showWarnings,
                        onCheckedChange = { onToggleWarnings() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFF7DF1E),
                            checkedTrackColor = Color(0xFFF7DF1E).copy(alpha = 0.3f),
                            uncheckedThumbColor = Color(0xFF6E6E78),
                            uncheckedTrackColor = Color(0xFF222228)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("サンプル警報のプレビュー表示", color = Color(0xFFEEEEF2), fontSize = 13.sp)
                        Text("※ 晴天時でも注意報バッジの表示を確認できます", color = Color(0xFF888894), fontSize = 10.sp)
                    }
                    Switch(
                        checked = preferences.demoWarningsPreview,
                        onCheckedChange = { onToggleDemoWarnings() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFF7DF1E),
                            checkedTrackColor = Color(0xFFF7DF1E).copy(alpha = 0.3f),
                            uncheckedThumbColor = Color(0xFF6E6E78),
                            uncheckedTrackColor = Color(0xFF222228)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("リアルタイム天気・気温表示", color = Color(0xFFEEEEF2), fontSize = 13.sp)
                    Switch(
                        checked = preferences.showWeather,
                        onCheckedChange = { onToggleWeather() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accentColor,
                            checkedTrackColor = accentColor.copy(alpha = 0.3f),
                            uncheckedThumbColor = Color(0xFF6E6E78),
                            uncheckedTrackColor = Color(0xFF222228)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Refresh Button
                Button(
                    onClick = onRefresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x22FFFFFF),
                        contentColor = Color(0xFFEEEEF2)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("気象庁・最新データを今すぐ更新", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
