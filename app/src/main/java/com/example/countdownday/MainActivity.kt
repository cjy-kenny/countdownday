package com.example.countdownday

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CountdownTheme {
                CountdownApp()
            }
        }
    }
}

/** Material 3 主题：Android 12+ 动态取色，低版本用默认方案；深色模式自适应 */
@Composable
fun CountdownTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // 偏好变化时通过 recreate() 整体重建 Activity，这里每次组合直接读最新值即可
    val appearance = remember { AppearancePrefs.get(context) }
    val dark = when (appearance.darkMode) {
        1 -> false // 浅色
        2 -> true  // 深色
        else -> isSystemInDarkTheme() // 跟随系统
    }
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val seed = ColorThemes.argb(appearance.themeSeedIndex)
    val colorScheme = when {
        dynamicSupported && appearance.useDynamicColor ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = Color(seed),
            secondary = Color(seed),
            tertiary = Color(seed)
        )
        else -> lightColorScheme(
            primary = Color(seed),
            secondary = Color(seed),
            tertiary = Color(seed)
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownApp(vm: EventViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val showAddDialog = remember { mutableStateOf(false) }
    val editTarget = remember { mutableStateOf<UiEvent?>(null) }
    val showSettings = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 安全区适配：Scaffold 之外的内容（空状态居中提示等）用系统栏 insets 兜底
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()

    // SAF 导出：系统保存文件对话框
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { vm.exportEvents(it) }
    }

    // 导出结果提示
    vm.message.value?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            vm.consumeMessage()
        }
    }

    // 通知权限：Android 13+ 首次进入主界面时申请一次
    if (Build.VERSION.SDK_INT >= 33) {
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* 用户拒绝则不再打扰，Worker 会静默跳过 */ }
        LaunchedEffect(Unit) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("倒数日") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        exportLauncher.launch("countdown_export_$stamp.json")
                    }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "导出数据")
                    }
                    IconButton(onClick = { showSettings.value = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog.value = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text("+", fontSize = 26.sp)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.events.isEmpty()) {
                // 空状态不经过 Scaffold 内容 insets 的列表，手动补足系统栏间距
                EmptyState(
                    Modifier.align(Alignment.Center).padding(
                        bottom = navigationBarPadding.calculateBottomPadding() / 2
                    )
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.events, key = { it.id }) { event ->
                        SwipeableEventCard(
                            event = event,
                            onClick = { editTarget.value = event },
                            onDelete = {
                                vm.deleteEvent(event)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "已删除「${event.name}」",
                                        actionLabel = "撤销",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        vm.undoDelete()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog.value) {
        AddEventDialog(
            onDismiss = { showAddDialog.value = false },
            onConfirm = { name, millis, reminder, minutes, category, note, pinned, repeat, color ->
                vm.addEvent(name, millis, reminder, minutes, category, note, pinned, repeat, color)
                showAddDialog.value = false
            }
        )
    }

    editTarget.value?.let { target ->
        EditEventDialog(
            event = target,
            onDismiss = { editTarget.value = null },
            onSave = { name, millis, reminder, minutes, category, note, pinned, repeat, color ->
                vm.updateEvent(target.id, name, millis, reminder, minutes, category, note, pinned, repeat, color)
                editTarget.value = null
            },
            onDelete = {
                vm.deleteEvent(target)
                editTarget.value = null
            }
        )
    }

    if (showSettings.value) {
        SettingsDialog(
            onDismiss = { showSettings.value = false },
            onAppearanceChanged = {
                showSettings.value = false
                (context as? MainActivity)?.recreate()
            },
            onOpenBattery = {
                showSettings.value = false
                openBatteryOptimizationSettings(context)
            },
            onOpenExactAlarm = {
                showSettings.value = false
                openExactAlarmSettings(context)
            },
        )
    }
}

/** 跳转系统"电池优化不受限"设置页（各家 ROM 大多会落到应用详情/电池页） */
private fun openBatteryOptimizationSettings(context: android.content.Context) {
    try {
        // 优先尝试直接打开应用详情页（用户可在里面关电池限制）
        val detail = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", context.packageName, null)
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(detail)
    } catch (e: Exception) {
        try {
            val ignore = android.content.Intent(
                android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(ignore)
        } catch (ignored: Exception) {
            android.widget.Toast.makeText(context, "未找到电池设置入口", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

/** Android 12+：跳转"闹钟和提醒"（SCHEDULE_EXACT_ALARM）授权页 */
private fun openExactAlarmSettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < 31) {
        android.widget.Toast.makeText(context, "当前系统无需此权限", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            android.net.Uri.fromParts("package", context.packageName, null)
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "未找到精准闹钟设置入口", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenExactAlarm: () -> Unit,
    onAppearanceChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val exactGranted = remember { AlarmScheduler.canScheduleExact(context) }
    val appearance = remember { AppearancePrefs.get(context) }
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("外观", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                // 深色模式：跟随系统 / 浅色 / 深色
                Text("深色模式", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("跟随系统" to 0, "浅色" to 1, "深色" to 2).forEach { (label, value) ->
                        FilterChip(
                            selected = appearance.darkMode == value,
                            onClick = {
                                AppearancePrefs.save(context, darkMode = value)
                                onAppearanceChanged()
                            },
                            label = { Text(label, fontSize = 13.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // 主题色：Android 12+ 可选动态取色；否则 5 色种子
                if (dynamicSupported) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "跟随壁纸动态取色",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = appearance.useDynamicColor,
                            onCheckedChange = {
                                AppearancePrefs.save(context, useDynamicColor = it)
                                onAppearanceChanged()
                            }
                        )
                    }
                }
                if (!dynamicSupported || !appearance.useDynamicColor) {
                    Spacer(Modifier.height(6.dp))
                    Text("主题色", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ColorThemes.all.forEachIndexed { index, theme ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(Color(theme.argb), CircleShape)
                                    .clickable {
                                        AppearancePrefs.save(context, themeSeedIndex = index)
                                        onAppearanceChanged()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (appearance.themeSeedIndex == index) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = theme.label,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBattery() }
                        .padding(vertical = 12.dp)
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("后台运行设置", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "关闭电池限制，防止提醒被系统拦截",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenExactAlarm() }
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("精准提醒权限", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                if (exactGranted) "已授权，提醒将准时到达" else "未授权，提醒可能延迟（降级为每小时检查）",
                                fontSize = 12.sp,
                                color = if (exactGranted) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/** 列表为空时的空状态提示 */
@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_empty_calendar),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "还没有倒数日",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "点击右下角的 +，添加你的第一个倒数日\n生日、纪念日、截止日期，都不会再错过",
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 支持左滑删除的事件卡片 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableEventCard(event: UiEvent, onClick: () -> Unit, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error, MaterialTheme.shapes.medium)
                    .padding(end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }
    ) {
        EventCard(event, onClick)
    }
}

@Composable
fun EventCard(event: UiEvent, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // 主题色侧边条（colorTheme=0 蓝色时也显示，统一视觉语言）
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(Color(ColorThemes.argb(event.colorTheme)))
            )
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 分类小标签
                        Text(
                            event.category,
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier
                                .background(
                                    Color(ColorThemes.argb(event.colorTheme)),
                                    MaterialTheme.shapes.small
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                        if (event.isPinned) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "置顶",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (event.repeatRule != "不重复") {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                event.repeatRule,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        event.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (event.note.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            event.note,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            event.dateText,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (event.reminder) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                DateUtils.formatTime(event.reminderMinutes),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        when {
                            event.days > 0 -> "${event.days}"
                            event.days == 0 -> "今天"
                            else -> "${-event.days}"
                        },
                        fontSize = if (event.days == 0) 20.sp else 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            event.days > 0 -> Color(ColorThemes.argb(event.colorTheme))
                            event.days == 0 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.outline
                        }
                    )
                    if (event.days != 0) {
                        Text(
                            if (event.days > 0) "天后" else "天前",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 添加 / 编辑共用富表单：名称+分类+日期+备注+重复+提醒开关+提醒时间+颜色+置顶，可滚动 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDialog(
    title: String,
    initialName: String,
    initialDate: Long?,
    initialReminder: Boolean,
    initialMinutes: Int,
    initialCategory: String,
    initialNote: String,
    initialPinned: Boolean,
    initialRepeat: String,
    initialColor: Int,
    confirmLabel: String,
    dismissLabel: String,
    showDelete: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, Boolean, Int, String, String, Boolean, String, Int) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var reminder by remember { mutableStateOf(initialReminder) }
    var reminderMinutes by remember { mutableStateOf(initialMinutes) }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var category by remember { mutableStateOf(initialCategory) }
    var note by remember { mutableStateOf(initialNote) }
    var pinned by remember { mutableStateOf(initialPinned) }
    var repeatRule by remember { mutableStateOf(initialRepeat) }
    var colorIndex by remember { mutableStateOf(initialColor) }
    var repeatMenuOpen by remember { mutableStateOf(false) }

    val inputValid = name.isNotBlank() && selectedDate != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("事件名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("分类", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("生日", "纪念日", "考试", "节日", "其他").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 13.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box {
                    OutlinedTextField(
                        value = selectedDate?.let { DateUtils.format(it) } ?: "",
                        onValueChange = {},
                        label = { Text("目标日期") },
                        placeholder = { Text("点击选择日期") },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // readOnly TextField 自身消费点击，用透明覆盖层接管整块点击区域
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable { showDatePicker = true }
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                ExposedDropdownMenuBox(
                    expanded = repeatMenuOpen,
                    onExpandedChange = { repeatMenuOpen = it }
                ) {
                    OutlinedTextField(
                        value = repeatRule,
                        onValueChange = {},
                        label = { Text("重复规则") },
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = repeatMenuOpen)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = repeatMenuOpen,
                        onDismissRequest = { repeatMenuOpen = false }
                    ) {
                        listOf("不重复", "每年", "每月").forEach { rule ->
                            DropdownMenuItem(
                                text = { Text(rule) },
                                onClick = {
                                    repeatRule = rule
                                    repeatMenuOpen = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { reminder = !reminder }
                ) {
                    Checkbox(checked = reminder, onCheckedChange = { reminder = it })
                    Text("当天提醒我", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                if (reminder) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTimePicker = true }
                            .padding(start = 16.dp, bottom = 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "提醒时间：" + DateUtils.formatTime(reminderMinutes) + "（点击修改）",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("卡片颜色", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColorThemes.all.forEachIndexed { index, theme ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(theme.argb), CircleShape)
                                .clickable { colorIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            if (colorIndex == index) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = theme.label,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("置顶显示", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Switch(checked = pinned, onCheckedChange = { pinned = it })
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = inputValid,
                onClick = {
                    onConfirm(
                        name.trim(),
                        DateUtils.normalize(selectedDate!!),
                        reminder,
                        reminderMinutes,
                        category,
                        note.trim(),
                        pinned,
                        repeatRule,
                        colorIndex
                    )
                }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                if (showDelete && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text(dismissLabel) }
            }
        }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate ?: DateUtils.today().timeInMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        pickerState.selectedDateMillis?.let { selectedDate = it }
                        showDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = reminderMinutes / 60,
            initialMinute = reminderMinutes % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择提醒时间") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    reminderMinutes = timeState.hour * 60 + timeState.minute
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun AddEventDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Long, Boolean, Int, String, String, Boolean, String, Int) -> Unit
) {
    EventDialog(
        title = "添加倒数日",
        initialName = "",
        initialDate = null,
        initialReminder = true,
        initialMinutes = 9 * 60,
        initialCategory = "其他",
        initialNote = "",
        initialPinned = false,
        initialRepeat = "不重复",
        initialColor = 0,
        confirmLabel = "添加",
        dismissLabel = "取消",
        showDelete = false,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
fun EditEventDialog(
    event: UiEvent,
    onDismiss: () -> Unit,
    onSave: (String, Long, Boolean, Int, String, String, Boolean, String, Int) -> Unit,
    onDelete: () -> Unit
) {
    EventDialog(
        title = "编辑事件",
        initialName = event.name,
        initialDate = event.targetMillis,
        initialReminder = event.reminder,
        initialMinutes = event.reminderMinutes,
        initialCategory = event.category,
        initialNote = event.note,
        initialPinned = event.isPinned,
        initialRepeat = event.repeatRule,
        initialColor = event.colorTheme,
        confirmLabel = "保存",
        dismissLabel = "取消",
        showDelete = true,
        onDismiss = onDismiss,
        onConfirm = onSave,
        onDelete = onDelete
    )
}
