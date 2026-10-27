package com.example.countdownday

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiEvent(
    val id: Long,
    val name: String,
    val dateText: String,
    val targetMillis: Long,
    val days: Int,
    val reminder: Boolean,
    val reminderMinutes: Int,
    val category: String = "其他",
    val note: String = "",
    val isPinned: Boolean = false,
    val repeatRule: String = "不重复",
    val colorTheme: Int = 0
)

data class UiState(
    val events: List<UiEvent> = emptyList(),
    val loading: Boolean = true
)

class EventViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = EventRepository(AppDatabase.get(app).eventDao())

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 最近一次被删除的事件，用于"撤销"恢复 */
    private var lastDeleted: UiEvent? = null

    init {
        // 订阅 Room 数据流，任何增删改都会自动刷新 UI
        viewModelScope.launch {
            repository.events.collect { list ->
                val mapped = list.map {
                    UiEvent(
                        id = it.id,
                        name = it.name,
                        dateText = DateUtils.format(it.targetDate),
                        targetMillis = it.targetDate,
                        days = DateUtils.daysFromToday(it.targetDate),
                        reminder = it.reminderEnabled,
                        reminderMinutes = it.reminderMinutes,
                        category = it.category,
                        note = it.note,
                        isPinned = it.isPinned,
                        repeatRule = it.repeatRule,
                        colorTheme = it.colorTheme
                    )
                }
                // 排序：置顶永远最前 → 未过期按天数升序 → 已过期按过去天数升序（近的在前）
                val pinned = mapped.filter { it.isPinned }.sortedBy { it.days }
                val future = mapped.filter { !it.isPinned && it.days >= 0 }.sortedBy { it.days }
                val past = mapped.filter { !it.isPinned && it.days < 0 }.sortedBy { -it.days }
                _uiState.update { it.copy(events = pinned + future + past, loading = false) }
                WidgetUpdater.updateAll(app)
            }
        }
    }

    fun addEvent(
        name: String, dateMillis: Long, reminder: Boolean, reminderMinutes: Int,
        category: String, note: String, isPinned: Boolean, repeatRule: String, colorTheme: Int
    ) {
        viewModelScope.launch {
            repository.add(name, dateMillis, reminder, reminderMinutes, category, note, isPinned, repeatRule, colorTheme)
            AlarmScheduler.rescheduleAll(getApplication())
            if (reminder) {
                ReminderScheduler.scheduleSameDayCatchUp(getApplication(), dateMillis, reminderMinutes)
            }
        }
    }

    fun updateEvent(
        id: Long, name: String, dateMillis: Long, reminder: Boolean, reminderMinutes: Int,
        category: String, note: String, isPinned: Boolean, repeatRule: String, colorTheme: Int
    ) {
        viewModelScope.launch {
            repository.update(id, name, dateMillis, reminder, reminderMinutes, category, note, isPinned, repeatRule, colorTheme)
            AlarmScheduler.rescheduleAll(getApplication())
            if (reminder) {
                ReminderScheduler.scheduleSameDayCatchUp(getApplication(), dateMillis, reminderMinutes)
            }
        }
    }

    /** 滑动删除：先记下完整事件，配合 Snackbar 的"撤销"可恢复 */
    fun deleteEvent(event: UiEvent) {
        lastDeleted = event
        viewModelScope.launch {
            repository.delete(event.id)
            AlarmScheduler.rescheduleAll(getApplication())
        }
    }

    fun undoDelete() {
        val snapshot = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            repository.restore(snapshot)
            AlarmScheduler.rescheduleAll(getApplication())
        }
    }

    /** SAF：用户已在系统对话框选好保存位置，把数据写到所选 uri */
    fun exportEvents(uri: Uri) {
        viewModelScope.launch {
            _message.value = repository.exportJson(getApplication(), uri)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
