package com.ventricle.assessment.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventricle.assessment.backup.ExcelBackup
import com.ventricle.assessment.data.local.ReminderEntity
import com.ventricle.assessment.data.repository.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

private data class ReminderDraft(
    val id: Long = 0,
    val title: String = "",
    val notes: String = "",
    val dateTimeMillis: Long = System.currentTimeMillis() + 3600_000,
    val patientName: String = "",
    val mrdNumber: String = "",
    val diagnosisNote: String = "",
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toEntity() = ReminderEntity(
        id = id,
        title = title.trim(),
        notes = notes.trim(),
        dateTimeMillis = dateTimeMillis,
        patientName = patientName.trim(),
        mrdNumber = mrdNumber.trim(),
        diagnosisNote = diagnosisNote.trim(),
        isDone = isDone,
        createdAt = createdAt
    )

    companion object {
        fun from(r: ReminderEntity) = ReminderDraft(
            id = r.id,
            title = r.title,
            notes = r.notes,
            dateTimeMillis = r.dateTimeMillis,
            patientName = r.patientName,
            mrdNumber = r.mrdNumber,
            diagnosisNote = r.diagnosisNote,
            isDone = r.isDone,
            createdAt = r.createdAt
        )
    }
}

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val excelBackup: ExcelBackup
) : ViewModel() {

    val reminders: StateFlow<List<ReminderEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun canScheduleExactAlarms(): Boolean = repository.canScheduleExactAlarms()

    fun save(draft: ReminderDraft) {
        viewModelScope.launch {
            val entity = draft.toEntity()
            repository.save(entity)
            val willFire = entity.dateTimeMillis > System.currentTimeMillis() && !entity.isDone
            _messages.emit(
                if (willFire) "Reminder saved — notification scheduled"
                else "Saved, but the time has passed — no notification will fire"
            )
        }
    }

    fun setDone(id: Long, done: Boolean) {
        viewModelScope.launch { repository.setDone(id, done) }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            _messages.emit("Reminder deleted")
        }
    }

    fun exportReminders(onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            val list = reminders.value
            if (list.isEmpty()) {
                _messages.emit("Nothing to export")
                return@launch
            }
            onReady(excelBackup.exportReminders(list))
        }
    }
}

private fun startOfDay(millis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(viewModel: RemindersViewModel = hiltViewModel()) {
    val reminders by viewModel.reminders.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    var tab by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<ReminderDraft?>(null) }
    var pendingDelete by remember { mutableStateOf<ReminderEntity?>(null) }

    val notifLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Re-read on every composition so returning from system settings refreshes the banners.
    val canExact = viewModel.canScheduleExactAlarms()
    val notifGranted = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else true

    fun shareXlsx(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share reminders"))
    }

    val dateTimeFmt = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.exportReminders { shareXlsx(it) } }) {
                        Icon(Icons.Filled.Upload, contentDescription = "Export reminders")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { draft = ReminderDraft() }) {
                Icon(Icons.Filled.Add, contentDescription = "New reminder")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (Build.VERSION.SDK_INT >= 31 && !canExact) {
                PermissionBanner(
                    text = "Exact alarms are off — reminders may not fire on time.",
                    actionLabel = "Grant",
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    }
                )
            }
            if (!notifGranted) {
                PermissionBanner(
                    text = "Notifications are off — you won't see reminder alerts.",
                    actionLabel = "Enable",
                    onAction = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("List") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Calendar") }
                )
            }
            if (tab == 0) {
                ReminderListView(
                    reminders = reminders,
                    dateTimeFmt = dateTimeFmt,
                    onEdit = { draft = ReminderDraft.from(it) },
                    onToggleDone = viewModel::setDone,
                    onDelete = { pendingDelete = it }
                )
            } else {
                CalendarTab(
                    reminders = reminders,
                    dateTimeFmt = dateTimeFmt,
                    onEdit = { draft = ReminderDraft.from(it) },
                    onToggleDone = viewModel::setDone,
                    onDelete = { pendingDelete = it }
                )
            }
        }
    }

    draft?.let { d ->
        ReminderEditDialog(
            draft = d,
            onDraftChange = { draft = it },
            onDismiss = { draft = null },
            onSave = { viewModel.save(it); draft = null }
        )
    }

    pendingDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete reminder?") },
            text = { Text("Delete \"${r.title.ifBlank { "this reminder" }}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.delete(r.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PermissionBanner(text: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun ReminderListView(
    reminders: List<ReminderEntity>,
    dateTimeFmt: SimpleDateFormat,
    onEdit: (ReminderEntity) -> Unit,
    onToggleDone: (Long, Boolean) -> Unit,
    onDelete: (ReminderEntity) -> Unit
) {
    if (reminders.isEmpty()) {
        EmptyReminders()
        return
    }
    val now = System.currentTimeMillis()
    val upcoming = reminders.filter { !it.isDone }.sortedBy { it.dateTimeMillis }
    val done = reminders.filter { it.isDone }.sortedByDescending { it.dateTimeMillis }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (upcoming.isNotEmpty()) {
            item { Text("Upcoming", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            items(upcoming, key = { it.id }) { r ->
                ReminderRow(r, r.dateTimeMillis < now, dateTimeFmt, onToggleDone, onEdit, onDelete)
            }
        }
        if (done.isNotEmpty()) {
            item {
                Text(
                    "Done",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (upcoming.isNotEmpty()) 8.dp else 0.dp)
                )
            }
            items(done, key = { it.id }) { r ->
                ReminderRow(r, false, dateTimeFmt, onToggleDone, onEdit, onDelete)
            }
        }
    }
}

@Composable
private fun ReminderRow(
    r: ReminderEntity,
    overdue: Boolean,
    dateTimeFmt: SimpleDateFormat,
    onToggleDone: (Long, Boolean) -> Unit,
    onEdit: (ReminderEntity) -> Unit,
    onDelete: (ReminderEntity) -> Unit
) {
    Card(
        onClick = { onEdit(r) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = r.isDone,
                onCheckedChange = { onToggleDone(r.id, it) }
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = r.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (overdue) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Overdue",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(
                    text = dateTimeFmt.format(Date(r.dateTimeMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (r.patientName.isNotBlank() || r.mrdNumber.isNotBlank()) {
                    Text(
                        text = buildString {
                            append(r.patientName.ifBlank { "—" })
                            if (r.mrdNumber.isNotBlank()) append(" · ${r.mrdNumber}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (r.notes.isNotBlank()) {
                    Text(
                        text = r.notes,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (r.diagnosisNote.isNotBlank()) {
                    Text(
                        text = r.diagnosisNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = { onDelete(r) }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete reminder",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EmptyReminders() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No reminders yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap + to add a follow-up reminder — it will notify you on the exact date and time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------------- calendar ----------------

@Composable
private fun CalendarTab(
    reminders: List<ReminderEntity>,
    dateTimeFmt: SimpleDateFormat,
    onEdit: (ReminderEntity) -> Unit,
    onToggleDone: (Long, Boolean) -> Unit,
    onDelete: (ReminderEntity) -> Unit
) {
    var monthOffset by remember { mutableIntStateOf(0) }
    var selectedDay by remember { mutableLongStateOf(startOfDay(System.currentTimeMillis())) }
    val todayStart = remember { startOfDay(System.currentTimeMillis()) }

    val monthCal = remember(monthOffset) {
        Calendar.getInstance().apply {
            add(Calendar.MONTH, monthOffset)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val monthTitle = remember(monthCal) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(monthCal.time)
    }
    val byDay = remember(reminders) { reminders.groupBy { startOfDay(it.dateTimeMillis) } }

    val firstDow = monthCal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday
    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells: List<Int?> = List(firstDow - 1) { null } + (1..daysInMonth).toList()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { monthOffset-- }) { Text("‹ Prev") }
            Text(monthTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { monthOffset++ }) { Text("Next ›") }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                Text(
                    text = d,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            userScrollEnabled = false
        ) {
            items(cells.size) { idx ->
                val day = cells[idx]
                if (day == null) {
                    Box(modifier = Modifier.aspectRatio(1f))
                } else {
                    val dayStart = Calendar.getInstance().apply {
                        set(
                            monthCal.get(Calendar.YEAR),
                            monthCal.get(Calendar.MONTH),
                            day, 0, 0, 0
                        )
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val dayReminders = byDay[dayStart].orEmpty()
                    val isSelected = dayStart == selectedDay
                    val isToday = dayStart == todayStart
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .then(
                                if (isToday && !isSelected) {
                                    Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                                } else Modifier
                            )
                            .clickable { selectedDay = dayStart },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = day.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    dayReminders.isNotEmpty() -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(8.dp)
                            ) {
                                dayReminders.take(3).forEach { r ->
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                                    r.isDone -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    else -> MaterialTheme.colorScheme.primary
                                                }
                                            )
                                    )
                                }
                                if (dayReminders.size > 3) {
                                    Text(
                                        text = "+${dayReminders.size - 3}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val selectedList = byDay[selectedDay].orEmpty().sortedBy { it.dateTimeMillis }
        val selFmt = remember(selectedDay) {
            SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(selectedDay))
        }
        Text(
            text = selFmt,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (selectedList.isEmpty()) {
            Text(
                text = "No reminders on this day",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(selectedList, key = { it.id }) { r ->
                    val now = System.currentTimeMillis()
                    ReminderRow(
                        r = r,
                        overdue = !r.isDone && r.dateTimeMillis < now,
                        dateTimeFmt = dateTimeFmt,
                        onToggleDone = onToggleDone,
                        onEdit = onEdit,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

// ---------------- add / edit dialog ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditDialog(
    draft: ReminderDraft,
    onDraftChange: (ReminderDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ReminderDraft) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = if (draft.id == 0L) "New reminder" else "Edit reminder",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { onDraftChange(draft.copy(title = it)) },
                    label = { Text("Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateFmt.format(Date(draft.dateTimeMillis)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Date") },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick date")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timeFmt.format(Date(draft.dateTimeMillis)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Time") },
                        trailingIcon = {
                            IconButton(onClick = { showTimePicker = true }) {
                                Icon(Icons.Filled.Schedule, contentDescription = "Pick time")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { onDraftChange(draft.copy(notes = it)) },
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft.patientName,
                    onValueChange = { onDraftChange(draft.copy(patientName = it)) },
                    label = { Text("Patient name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft.mrdNumber,
                    onValueChange = { onDraftChange(draft.copy(mrdNumber = it)) },
                    label = { Text("MRD number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft.diagnosisNote,
                    onValueChange = { onDraftChange(draft.copy(diagnosisNote = it)) },
                    label = { Text("Diagnosis note") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(draft) },
                        enabled = draft.title.isNotBlank()
                    ) { Text("Save") }
                }
            }
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = draft.dateTimeMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { picked ->
                        val current = Calendar.getInstance().apply { timeInMillis = draft.dateTimeMillis }
                        // Read the picked day in UTC: the picker returns UTC midnight,
                        // which is the previous evening in western timezones.
                        val p = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = picked
                        }
                        current.set(p.get(Calendar.YEAR), p.get(Calendar.MONTH), p.get(Calendar.DAY_OF_MONTH))
                        onDraftChange(draft.copy(dateTimeMillis = current.timeInMillis))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val c0 = Calendar.getInstance().apply { timeInMillis = draft.dateTimeMillis }
        val timeState = rememberTimePickerState(
            initialHour = c0.get(Calendar.HOUR_OF_DAY),
            initialMinute = c0.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = draft.dateTimeMillis }
                    c.set(Calendar.HOUR_OF_DAY, timeState.hour)
                    c.set(Calendar.MINUTE, timeState.minute)
                    c.set(Calendar.SECOND, 0)
                    c.set(Calendar.MILLISECOND, 0)
                    onDraftChange(draft.copy(dateTimeMillis = c.timeInMillis))
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = timeState) }
        )
    }
}
