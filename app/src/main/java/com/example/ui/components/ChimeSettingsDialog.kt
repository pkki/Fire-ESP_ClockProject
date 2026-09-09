package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.audio.ChimeSound
import com.example.model.ChimeAudioSourceType
import com.example.model.ChimeVideoSourceType
import com.example.model.ClockPreferencesState
import com.example.model.CustomAudioItem
import com.example.model.CustomVideoItem
import com.example.model.ScheduledChime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChimeSettingsDialog(
    preferences: ClockPreferencesState,
    scheduledChimes: List<ScheduledChime>,
    customAudioList: List<CustomAudioItem>,
    customVideoList: List<CustomVideoItem>,
    onDismiss: () -> Unit,
    onSaveChime: (ScheduledChime) -> Unit,
    onDeleteChime: (String) -> Unit,
    onToggleChime: (String) -> Unit,
    onImportCustomAudio: (Uri, (Boolean, String) -> Unit) -> Unit,
    onDeleteCustomAudio: (CustomAudioItem) -> Unit,
    onImportCustomVideo: (Uri, (Boolean, String) -> Unit) -> Unit,
    onDeleteCustomVideo: (CustomVideoItem) -> Unit,
    onTestPlayChime: (ScheduledChime) -> Unit,
    onTestPlayAudioFile: (String) -> Unit,
    onPreviewVideo: (ChimeVideoSourceType, String?, String?, Boolean, Int) -> Unit,
    onTestSound: (ChimeSound) -> Unit,
    onStopAudio: () -> Unit,
    onToggleHourlyChime: (Boolean) -> Unit,
    onToggleHalfHourlyChime: (Boolean) -> Unit,
    onSelectSound: (ChimeSound) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onHoursChange: (Int, Int) -> Unit
) {
    val accentColor = preferences.colorPalette.primary
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingChime by remember { mutableStateOf<ScheduledChime?>(null) }
    var isCreatingNewChime by remember { mutableStateOf(false) }

    // Audio file picker
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            onImportCustomAudio(it) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Video file picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            onImportCustomVideo(it) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
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
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "チャイム・アラーム & 背景動画設定",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "指定時刻のチャイム音と時計背景での動画再生演出",
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color(0x22FFFFFF),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable {
                                onStopAudio()
                                onDismiss()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "閉じる",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1A1A24),
                    contentColor = accentColor,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = accentColor,
                            height = 3.dp
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("チャイム一覧 (${scheduledChimes.size})", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("動画・音声管理", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("毎正時の時報", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Contents
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> ScheduledChimesTab(
                            scheduledChimes = scheduledChimes,
                            accentColor = accentColor,
                            onAddNew = { isCreatingNewChime = true },
                            onEdit = { editingChime = it },
                            onDelete = onDeleteChime,
                            onToggle = onToggleChime,
                            onTestPlay = onTestPlayChime
                        )
                        1 -> MediaManagementTab(
                            customAudioList = customAudioList,
                            customVideoList = customVideoList,
                            accentColor = accentColor,
                            onPickAudio = { audioPickerLauncher.launch("audio/*") },
                            onDeleteAudio = onDeleteCustomAudio,
                            onTestPlayAudio = onTestPlayAudioFile,
                            onPickVideo = { videoPickerLauncher.launch("video/*") },
                            onDeleteVideo = onDeleteCustomVideo,
                            onPreviewVideo = { videoItem ->
                                onPreviewVideo(ChimeVideoSourceType.CUSTOM_FILE, videoItem.filePath, videoItem.name, true, 30)
                            },
                            onPreviewPreset = { preset ->
                                onPreviewVideo(preset, null, null, false, 30)
                            },
                            onStopAudio = onStopAudio,
                            onCreateChimeWithMedia = { videoItem, audioItem ->
                                editingChime = ScheduledChime(
                                    hour = 12,
                                    minute = 0,
                                    label = videoItem?.name?.substringBeforeLast(".") ?: audioItem?.name?.substringBeforeLast(".") ?: "チャイム",
                                    sourceType = if (audioItem != null) ChimeAudioSourceType.CUSTOM_FILE else ChimeAudioSourceType.BUILT_IN,
                                    customAudioId = audioItem?.id,
                                    customAudioName = audioItem?.name,
                                    customAudioPath = audioItem?.filePath,
                                    videoSourceType = if (videoItem != null) ChimeVideoSourceType.CUSTOM_FILE else ChimeVideoSourceType.NONE,
                                    customVideoId = videoItem?.id,
                                    customVideoName = videoItem?.name,
                                    customVideoPath = videoItem?.filePath
                                )
                                isCreatingNewChime = true
                            }
                        )
                        2 -> HourlyChimeSettingsTab(
                            preferences = preferences,
                            accentColor = accentColor,
                            onToggleHourly = onToggleHourlyChime,
                            onToggleHalfHourly = onToggleHalfHourlyChime,
                            onSelectSound = onSelectSound,
                            onTestSound = onTestSound,
                            onVolumeChange = onVolumeChange,
                            onHoursChange = onHoursChange
                        )
                    }
                }
            }
        }
    }

    // Chime Editor Dialog (Add / Edit)
    if (isCreatingNewChime || editingChime != null) {
        val chimeToEdit = editingChime ?: ScheduledChime(
            hour = 12,
            minute = 0,
            label = "新しいチャイム",
            daysOfWeek = setOf(1, 2, 3, 4, 5)
        )

        ChimeEditorDialog(
            initialChime = chimeToEdit,
            customAudioList = customAudioList,
            customVideoList = customVideoList,
            accentColor = accentColor,
            onDismiss = {
                isCreatingNewChime = false
                editingChime = null
            },
            onSave = { savedChime ->
                onSaveChime(savedChime)
                isCreatingNewChime = false
                editingChime = null
            },
            onPickAudio = { audioPickerLauncher.launch("audio/*") },
            onPickVideo = { videoPickerLauncher.launch("video/*") },
            onPreview = { chime ->
                onTestPlayChime(chime)
            }
        )
    }
}

// -------------------------------------------------------------
// TAB 1: Scheduled Chimes List
// -------------------------------------------------------------

@Composable
fun ScheduledChimesTab(
    scheduledChimes: List<ScheduledChime>,
    accentColor: Color,
    onAddNew: () -> Unit,
    onEdit: (ScheduledChime) -> Unit,
    onDelete: (String) -> Unit,
    onToggle: (String) -> Unit,
    onTestPlay: (ScheduledChime) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "登録済みチャイム・アラーム一覧",
                color = Color(0xFFDDDDDD),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Button(
                onClick = onAddNew,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("新しいチャイムを追加", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (scheduledChimes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0x55FFFFFF),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "登録されたチャイムがありません",
                        color = Color(0xFFAAAAAA),
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onAddNew,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("＋ チャイムを作成する", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(scheduledChimes, key = { it.id }) { chime ->
                    ChimeCardItem(
                        chime = chime,
                        accentColor = accentColor,
                        onEdit = { onEdit(chime) },
                        onDelete = { onDelete(chime.id) },
                        onToggle = { onToggle(chime.id) },
                        onTestPlay = { onTestPlay(chime) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChimeCardItem(
    chime: ScheduledChime,
    accentColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
    onTestPlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (chime.isEnabled) Color(0xFF1E1E28) else Color(0xFF16161E)
        ),
        border = if (chime.isEnabled) androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Time & Label & Badges
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // Large Time Display
                Text(
                    text = chime.formattedTime,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (chime.isEnabled) accentColor else Color(0x66FFFFFF)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = chime.label,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (chime.isEnabled) Color.White else Color(0x77FFFFFF)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Days repeat badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x22FFFFFF),
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = chime.repeatDaysText,
                                color = Color(0xFFCCCCCC),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Sound info
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = chime.soundDisplayName,
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp
                            )
                        }

                        // Video info badge if set
                        if (chime.videoSourceType != ChimeVideoSourceType.NONE) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0x3364B5F6)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = Color(0xFF64B5F6),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "背景動画: ${chime.videoDisplayName}",
                                        color = Color(0xFF90CAF9),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Right: Actions & Switch
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Test Play Button
                IconButton(
                    onClick = onTestPlay,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "試聴・演出テスト",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Edit Button
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "編集",
                        tint = Color(0xFFCCCCCC),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "削除",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = chime.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = accentColor,
                        checkedTrackColor = accentColor.copy(alpha = 0.4f)
                    )
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: Media Management (Videos & Audio Files)
// -------------------------------------------------------------

@Composable
fun MediaManagementTab(
    customAudioList: List<CustomAudioItem>,
    customVideoList: List<CustomVideoItem>,
    accentColor: Color,
    onPickAudio: () -> Unit,
    onDeleteAudio: (CustomAudioItem) -> Unit,
    onTestPlayAudio: (String) -> Unit,
    onPickVideo: () -> Unit,
    onDeleteVideo: (CustomVideoItem) -> Unit,
    onPreviewVideo: (CustomVideoItem) -> Unit,
    onPreviewPreset: (ChimeVideoSourceType) -> Unit,
    onStopAudio: () -> Unit,
    onCreateChimeWithMedia: (CustomVideoItem?, CustomAudioItem?) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 8.dp)
    ) {
        // --- SECTION 1: Video Files (User MP4 Videos & Presets) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B26)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("🎬 背景動画ファイル (MP4 / WebM)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("指定時刻に時計の背景に全画面で映し出されます", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = onPickVideo,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("動画を追加", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (customVideoList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF13131A))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "端末から動画ファイル (MP4 / WebM) が追加されていません",
                                color = Color(0xFF888888),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "右上の「動画を追加」から好きな動画を取り込んで背景に設定できます",
                                color = Color(0xFF666666),
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customVideoList.forEach { videoItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF13131A))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(videoItem.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date(videoItem.dateAdded)),
                                            color = Color(0xFF777777),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Preview Video Button
                                    Button(
                                        onClick = { onPreviewVideo(videoItem) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x3364B5F6)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF90CAF9), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("背景プレビュー", color = Color(0xFF90CAF9), fontSize = 12.sp)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Create Chime Shortcut
                                    Button(
                                        onClick = { onCreateChimeWithMedia(videoItem, null) },
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("チャイムに設定", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    IconButton(
                                        onClick = { onDeleteVideo(videoItem) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "削除", tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Built-in Preset Animations Preview
                Text("✨ プリセット背景動画演出 (内蔵)", color = Color(0xFFDDDDDD), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        ChimeVideoSourceType.PRESET_AURORA,
                        ChimeVideoSourceType.PRESET_FIREPLACE,
                        ChimeVideoSourceType.PRESET_STARRY_NIGHT,
                        ChimeVideoSourceType.PRESET_RAIN,
                        ChimeVideoSourceType.PRESET_SUNRISE
                    )
                    presets.forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF242432),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPreviewPreset(preset) }
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(preset.displayName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("プレビュー", color = accentColor, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SECTION 2: Custom Audio Files ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B26)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("🔔 オリジナル音声ファイル (MP3 / WAV / M4A)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("学校チャイムや好きなジングル音声を自由に追加", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = onPickAudio,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("音声を追加", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (customAudioList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF13131A))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "端末から音声ファイルが追加されていません。右上の「音声を追加」から追加できます。",
                            color = Color(0xFF888888),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customAudioList.forEach { audioItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF13131A))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(audioItem.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date(audioItem.dateAdded)),
                                            color = Color(0xFF777777),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onTestPlayAudio(audioItem.filePath) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "試聴", tint = Color(0xFF81C784), modifier = Modifier.size(20.dp))
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Button(
                                        onClick = { onCreateChimeWithMedia(null, audioItem) },
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("チャイムに設定", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    IconButton(
                                        onClick = { onDeleteAudio(audioItem) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "削除", tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
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

// -------------------------------------------------------------
// TAB 3: Hourly Chime Settings
// -------------------------------------------------------------

@Composable
fun HourlyChimeSettingsTab(
    preferences: ClockPreferencesState,
    accentColor: Color,
    onToggleHourly: (Boolean) -> Unit,
    onToggleHalfHourly: (Boolean) -> Unit,
    onSelectSound: (ChimeSound) -> Unit,
    onTestSound: (ChimeSound) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onHoursChange: (Int, Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hourly Toggle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B26)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("毎正時 (00分) の時報", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("毎時間のちょうど0分にメロディを鳴らします", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    }
                    Switch(
                        checked = preferences.hourlyChimeEnabled,
                        onCheckedChange = onToggleHourly,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accentColor,
                            checkedTrackColor = accentColor.copy(alpha = 0.4f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("毎半時 (30分) のピン音", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("30分に控えめなシングルベル音でお知らせします", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    }
                    Switch(
                        checked = preferences.halfHourlyChimeEnabled,
                        onCheckedChange = onToggleHalfHourly,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accentColor,
                            checkedTrackColor = accentColor.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }

        // Built-in Sound Selection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B26)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("時報のチャイム音を選択", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                ChimeSound.values().forEach { sound ->
                    val isSelected = preferences.chimeSound == sound
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Color(0xFF13131A))
                            .border(
                                1.dp,
                                if (isSelected) accentColor else Color(0x22FFFFFF),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectSound(sound) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sound.displayName,
                                color = if (isSelected) accentColor else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = sound.description,
                                color = Color(0xFFAAAAAA),
                                fontSize = 11.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onTestSound(sound) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "試聴",
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "選択中",
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Volume & Active Hours Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B26)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("時報音量: ${(preferences.chimeVolume * 100).toInt()}%", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = preferences.chimeVolume,
                    onValueChange = onVolumeChange,
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    "鳴動時間帯: ${preferences.chimeStartHour}:00 〜 ${preferences.chimeEndHour}:00",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("夜間や就寝中の不要な時報を自動ミュートします", color = Color(0xFFAAAAAA), fontSize = 12.sp)

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = listOf(Pair(7, 22), Pair(8, 20), Pair(9, 18), Pair(0, 23))
                    val presetLabels = listOf("7時〜22時 (標準)", "8時〜20時 (短め)", "9時〜18時 (勤務時間)", "24時間いつでも")
                    presets.forEachIndexed { index, (s, e) ->
                        val isCurrent = preferences.chimeStartHour == s && preferences.chimeEndHour == e
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrent) accentColor.copy(alpha = 0.2f) else Color(0xFF13131A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isCurrent) accentColor else Color(0x22FFFFFF)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onHoursChange(s, e) }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    presetLabels[index],
                                    color = if (isCurrent) accentColor else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CHIME EDITOR DIALOG (Add / Edit Single Chime with Video Settings)
// -------------------------------------------------------------

@Composable
fun ChimeEditorDialog(
    initialChime: ScheduledChime,
    customAudioList: List<CustomAudioItem>,
    customVideoList: List<CustomVideoItem>,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSave: (ScheduledChime) -> Unit,
    onPickAudio: () -> Unit,
    onPickVideo: () -> Unit,
    onPreview: (ScheduledChime) -> Unit
) {
    var hour by remember { mutableIntStateOf(initialChime.hour) }
    var minute by remember { mutableIntStateOf(initialChime.minute) }
    var label by remember { mutableStateOf(initialChime.label) }
    var selectedDays by remember { mutableStateOf(initialChime.daysOfWeek) }

    var sourceType by remember { mutableStateOf(initialChime.sourceType) }
    var builtInSound by remember { mutableStateOf(initialChime.builtInSound) }
    var selectedCustomAudio by remember {
        mutableStateOf(customAudioList.find { it.id == initialChime.customAudioId } ?: customAudioList.firstOrNull())
    }
    var volume by remember { mutableFloatStateOf(initialChime.volume) }

    // Video settings
    var videoSourceType by remember { mutableStateOf(initialChime.videoSourceType) }
    var selectedCustomVideo by remember {
        mutableStateOf(customVideoList.find { it.id == initialChime.customVideoId } ?: customVideoList.firstOrNull())
    }
    var videoDurationSeconds by remember { mutableIntStateOf(initialChime.videoDurationSeconds) }
    var playVideoAudio by remember { mutableStateOf(initialChime.playVideoAudio) }

    val currentChimeConfig = ScheduledChime(
        id = initialChime.id,
        hour = hour,
        minute = minute,
        label = label.ifBlank { "チャイム" },
        isEnabled = initialChime.isEnabled,
        daysOfWeek = selectedDays,
        sourceType = sourceType,
        builtInSound = builtInSound,
        customAudioId = if (sourceType == ChimeAudioSourceType.CUSTOM_FILE) selectedCustomAudio?.id else null,
        customAudioName = if (sourceType == ChimeAudioSourceType.CUSTOM_FILE) selectedCustomAudio?.name else null,
        customAudioPath = if (sourceType == ChimeAudioSourceType.CUSTOM_FILE) selectedCustomAudio?.filePath else null,
        volume = volume,
        videoSourceType = videoSourceType,
        customVideoId = if (videoSourceType == ChimeVideoSourceType.CUSTOM_FILE) selectedCustomVideo?.id else null,
        customVideoName = if (videoSourceType == ChimeVideoSourceType.CUSTOM_FILE) selectedCustomVideo?.name else null,
        customVideoPath = if (videoSourceType == ChimeVideoSourceType.CUSTOM_FILE) selectedCustomVideo?.filePath else null,
        videoDurationSeconds = videoDurationSeconds,
        playVideoAudio = playVideoAudio
    )

    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF14141E))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialChime.label == "新しいチャイム") "チャイムの新規登録" else "チャイムの編集",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        shape = CircleShape,
                        color = Color(0x22FFFFFF),
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onDismiss() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Configs
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Time Picker (Hour & Minute Selector)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("鳴動時刻を設定", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // Hour Wheel / Buttons
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Button(
                                        onClick = { hour = (hour + 1) % 24 },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28283C)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(44.dp, 32.dp)
                                    ) {
                                        Text("▲", color = Color.White, fontSize = 12.sp)
                                    }
                                    Text(
                                        text = String.format(Locale.US, "%02d", hour),
                                        fontSize = 44.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = accentColor,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    Button(
                                        onClick = { hour = if (hour == 0) 23 else hour - 1 },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28283C)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(44.dp, 32.dp)
                                    ) {
                                        Text("▼", color = Color.White, fontSize = 12.sp)
                                    }
                                }

                                Text(
                                    text = ":",
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )

                                // Minute Wheel / Buttons
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Button(
                                        onClick = { minute = (minute + 1) % 60 },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28283C)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(44.dp, 32.dp)
                                    ) {
                                        Text("▲", color = Color.White, fontSize = 12.sp)
                                    }
                                    Text(
                                        text = String.format(Locale.US, "%02d", minute),
                                        fontSize = 44.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = accentColor,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    Button(
                                        onClick = { minute = if (minute == 0) 59 else minute - 1 },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28283C)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(44.dp, 32.dp)
                                    ) {
                                        Text("▼", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 2. Label & Quick Presets
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("チャイムのラベル・名前", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = label,
                                onValueChange = { label = it },
                                placeholder = { Text("例: 朝のチャイム、昼休み、退勤など", color = Color(0xFF666666)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = Color(0x33FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick label presets
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val labelPresets = listOf("朝のチャイム", "始業", "昼休み", "15時リフレッシュ", "終業", "就寝")
                                labelPresets.forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (label == tag) accentColor.copy(alpha = 0.2f) else Color(0xFF282838),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (label == tag) accentColor else Color(0x22FFFFFF)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { label = tag }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = tag,
                                                color = if (label == tag) accentColor else Color(0xFFDDDDDD),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Repeat Days
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("繰り返す曜日", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val daysMap = listOf(
                                    1 to "月", 2 to "火", 3 to "水", 4 to "木", 5 to "金", 6 to "土", 7 to "日"
                                )
                                daysMap.forEach { (dayIso, labelJa) ->
                                    val isSelected = selectedDays.contains(dayIso)
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) accentColor else Color(0xFF282838),
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clickable {
                                                selectedDays = if (isSelected) {
                                                    selectedDays - dayIso
                                                } else {
                                                    selectedDays + dayIso
                                                }
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = labelJa,
                                                color = if (isSelected) Color.Black else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick Day Presets
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val dayShortcuts = listOf(
                                    "平日のみ" to setOf(1, 2, 3, 4, 5),
                                    "毎日" to setOf(1, 2, 3, 4, 5, 6, 7),
                                    "週末のみ" to setOf(6, 7),
                                    "1回のみ" to emptySet<Int>()
                                )
                                dayShortcuts.forEach { (shortcutLabel, set) ->
                                    val isCurrent = selectedDays == set
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCurrent) accentColor.copy(alpha = 0.2f) else Color(0xFF282838),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isCurrent) accentColor else Color(0x22FFFFFF)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedDays = set }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        ) {
                                            Text(
                                                shortcutLabel,
                                                color = if (isCurrent) accentColor else Color(0xFFCCCCCC),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Sound Selection (Built-in or Custom Audio)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("🔔 チャイム音声", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (sourceType == ChimeAudioSourceType.BUILT_IN) accentColor else Color(0xFF282838),
                                        modifier = Modifier.clickable { sourceType = ChimeAudioSourceType.BUILT_IN }
                                    ) {
                                        Text(
                                            "内蔵チャイム",
                                            color = if (sourceType == ChimeAudioSourceType.BUILT_IN) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (sourceType == ChimeAudioSourceType.CUSTOM_FILE) accentColor else Color(0xFF282838),
                                        modifier = Modifier.clickable { sourceType = ChimeAudioSourceType.CUSTOM_FILE }
                                    ) {
                                        Text(
                                            "カスタム音声 (${customAudioList.size})",
                                            color = if (sourceType == ChimeAudioSourceType.CUSTOM_FILE) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (sourceType == ChimeAudioSourceType.BUILT_IN) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ChimeSound.values().forEach { sound ->
                                        val isSel = builtInSound == sound
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) accentColor.copy(alpha = 0.15f) else Color(0xFF14141E))
                                                .border(1.dp, if (isSel) accentColor else Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                                                .clickable { builtInSound = sound }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                sound.displayName,
                                                color = if (isSel) accentColor else Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (isSel) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (customAudioList.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("カスタム音声が登録されていません", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = onPickAudio,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("＋ 端末から音声ファイルを追加", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        customAudioList.forEach { audio ->
                                            val isSel = selectedCustomAudio?.id == audio.id
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSel) accentColor.copy(alpha = 0.15f) else Color(0xFF14141E))
                                                .border(1.dp, if (isSel) accentColor else Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                                                .clickable { selectedCustomAudio = audio }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    audio.name,
                                                    color = if (isSel) accentColor else Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                if (isSel) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("音量: ${(volume * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
                            }
                            Slider(
                                value = volume,
                                onValueChange = { volume = it },
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                            )
                        }
                    }

                    // 5. VIDEO BACKGROUND EFFECT SETTINGS (NEW!)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3364B5F6))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("🎬 背景動画の演出", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("指定時刻になると時計の背景に動画が映し出されます", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Video Source Options
                            val videoOptions = listOf(
                                ChimeVideoSourceType.NONE,
                                ChimeVideoSourceType.CUSTOM_FILE,
                                ChimeVideoSourceType.PRESET_AURORA,
                                ChimeVideoSourceType.PRESET_FIREPLACE,
                                ChimeVideoSourceType.PRESET_STARRY_NIGHT,
                                ChimeVideoSourceType.PRESET_RAIN,
                                ChimeVideoSourceType.PRESET_SUNRISE
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                videoOptions.forEach { opt ->
                                    val isSel = videoSourceType == opt
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) Color(0x2264B5F6) else Color(0xFF14141E))
                                            .border(1.dp, if (isSel) Color(0xFF64B5F6) else Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                                            .clickable { videoSourceType = opt }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                opt.displayName,
                                                color = if (isSel) Color(0xFF90CAF9) else Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(opt.description, color = Color(0xFF888888), fontSize = 10.sp)
                                        }
                                        if (isSel) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            // If CUSTOM_FILE selected, choose which video file
                            if (videoSourceType == ChimeVideoSourceType.CUSTOM_FILE) {
                                Spacer(modifier = Modifier.height(10.dp))
                                if (customVideoList.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("端末から動画ファイルが追加されていません", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = onPickVideo,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("＋ 端末から動画を追加 (MP4/WebM)", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    Text("使用する動画ファイルを選択:", color = Color(0xFFDDDDDD), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        customVideoList.forEach { videoItem ->
                                            val isSel = selectedCustomVideo?.id == videoItem.id
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSel) Color(0x3364B5F6) else Color(0xFF101018))
                                                    .border(1.dp, if (isSel) Color(0xFF64B5F6) else Color(0x22FFFFFF), RoundedCornerShape(6.dp))
                                                    .clickable { selectedCustomVideo = videoItem }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(videoItem.name, color = if (isSel) Color(0xFF90CAF9) else Color.White, fontSize = 12.sp)
                                                if (isSel) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Extra video options (Duration & Video Audio)
                            if (videoSourceType != ChimeVideoSourceType.NONE) {
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("動画の音声を再生する", color = Color.White, fontSize = 13.sp)
                                    Switch(
                                        checked = playVideoAudio,
                                        onCheckedChange = { playVideoAudio = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF64B5F6))
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text("背景動画の表示時間:", color = Color(0xFFDDDDDD), fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val durationOptions = listOf(
                                        30 to "30秒",
                                        60 to "1分",
                                        180 to "3分",
                                        300 to "5分",
                                        0 to "停止まで"
                                    )
                                    durationOptions.forEach { (sec, labelText) ->
                                        val isSel = videoDurationSeconds == sec
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSel) Color(0xFF1976D2) else Color(0xFF282838),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { videoDurationSeconds = sec }
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                                Text(
                                                    labelText,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Buttons (Preview & Save)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Preview Button
                    Button(
                        onClick = { onPreview(currentChimeConfig) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28283C)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("この設定をプレビュー", color = Color.White, fontSize = 13.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("キャンセル", color = Color.White)
                        }

                        Button(
                            onClick = { onSave(currentChimeConfig) },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("保存する", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
