package com.ventricle.assessment.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventricle.assessment.data.local.AssessmentFormEntity
import com.ventricle.assessment.data.local.PatientEntity
import android.content.Intent
import android.net.Uri
import com.ventricle.assessment.backup.ExcelBackup
import com.ventricle.assessment.backup.ParsedBackup
import com.ventricle.assessment.data.repository.PatientRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ventricle.assessment.ui.components.pathwayColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import javax.inject.Inject

enum class PatientFilter { ALL, IN_PROGRESS, COMPLETED }

data class PatientListRow(
    val patient: PatientEntity,
    val pathway: String?,
    val answered: Int,
    val total: Int
)

@HiltViewModel
class PatientListViewModel @Inject constructor(
    private val repository: PatientRepository,
    private val excelBackup: ExcelBackup,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _pendingImport = MutableStateFlow<ParsedBackup?>(null)
    val pendingImport: StateFlow<ParsedBackup?> = _pendingImport.asStateFlow()

    private val _filter = MutableStateFlow(PatientFilter.ALL)
    val filter: StateFlow<PatientFilter> = _filter.asStateFlow()

    fun setFilter(f: PatientFilter) {
        _filter.value = f
    }

    val rows: StateFlow<List<PatientListRow>> = combine(
        repository.observePatients(),
        repository.observeForms(),
        _filter
    ) { patients, forms, filter ->
        val formsByPatient = forms.associateBy { it.patientId }
        patients
            .filter {
                when (filter) {
                    PatientFilter.ALL -> true
                    PatientFilter.IN_PROGRESS -> !it.isCompleted
                    PatientFilter.COMPLETED -> it.isCompleted
                }
            }
            .map { p ->
                val f = formsByPatient[p.id] ?: AssessmentFormEntity(patientId = p.id)
                val (a, t) = stepProgress(4, p, f)
                PatientListRow(p, f.psPathway, a, t)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasAnyPatients: StateFlow<Boolean> = repository.observePatients()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun newPatient(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createPatient()
            onCreated(id)
        }
    }

    fun deletePatient(patient: PatientEntity) {
        viewModelScope.launch { repository.deletePatient(patient.id) }
    }

    /** Builds the .xlsx backup and hands the shareable Uri to the caller. */
    fun exportBackup(onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            val patients = repository.observePatients().first()
            if (patients.isEmpty()) {
                _messages.emit("Nothing to export")
                return@launch
            }
            val forms = repository.observeForms().first()
            onReady(excelBackup.exportPatients(patients, forms))
        }
    }

    /** Parses a user-picked .xlsx file; shows the confirm dialog on success. */
    fun parseImport(uri: Uri) {
        viewModelScope.launch {
            try {
                val parsed = context.contentResolver.openInputStream(uri)
                    ?.use { excelBackup.parseBackup(it) }
                if (parsed == null || (parsed.patients.isEmpty() && parsed.forms.isEmpty())) {
                    _messages.emit("No patient data found in this file")
                } else {
                    _pendingImport.value = parsed
                }
            } catch (e: Exception) {
                _messages.emit("Couldn't read that file")
            }
        }
    }

    fun confirmImport() {
        val parsed = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch {
            repository.importPatients(parsed.patients)
            repository.importForms(parsed.forms)
            _messages.emit("Imported ${parsed.patients.size} patients, ${parsed.forms.size} assessments")
        }
    }

    fun dismissImport() {
        _pendingImport.value = null
    }
}

private fun filterLabel(f: PatientFilter) = when (f) {
    PatientFilter.ALL -> "All"
    PatientFilter.IN_PROGRESS -> "In progress"
    PatientFilter.COMPLETED -> "Completed"
}

@Composable
private fun FilterChipOption(
    value: PatientFilter,
    current: PatientFilter,
    onSelect: (PatientFilter) -> Unit
) {
    val icon: ImageVector = when (value) {
        PatientFilter.ALL -> Icons.Filled.List
        PatientFilter.IN_PROGRESS -> Icons.Filled.HourglassEmpty
        PatientFilter.COMPLETED -> Icons.Filled.CheckCircle
    }
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(filterLabel(value)) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PatientListScreen(
    onOpenPatient: (Long) -> Unit,
    viewModel: PatientListViewModel = hiltViewModel()
) {
    val rows by viewModel.rows.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val hasAny by viewModel.hasAnyPatients.collectAsState()
    val pendingImport by viewModel.pendingImport.collectAsState()
    var pendingDelete by remember { mutableStateOf<PatientEntity?>(null) }
    var removingId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) viewModel.parseImport(uri)
        }

    fun shareXlsx(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share backup"))
    }

    var fabIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { fabIn = true }
    val fabScale by animateFloatAsState(
        targetValue = if (fabIn) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
        label = "fab_scale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Patients", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportBackup { shareXlsx(it) } }) {
                        Icon(Icons.Filled.Upload, contentDescription = "Export backup (.xlsx)")
                    }
                    IconButton(onClick = {
                        importLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    }) {
                        Icon(Icons.Filled.Download, contentDescription = "Import backup (.xlsx)")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.newPatient(onOpenPatient) },
                modifier = Modifier.graphicsLayer {
                    scaleX = fabScale
                    scaleY = fabScale
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New patient")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PatientFilter.entries.forEach { f ->
                    FilterChipOption(
                        value = f,
                        current = filter,
                        onSelect = viewModel::setFilter
                    )
                }
            }
            when {
                !hasAny -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonSearch,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No patients yet",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap + to add your first patient and start their bi-ventricular assessment.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                rows.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No ${filterLabel(filter).lowercase()} patients",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(rows, key = { it.patient.id }) { row ->
                            val p = row.patient
                            AnimatedVisibility(
                                visible = removingId != p.id,
                                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(300)),
                                modifier = Modifier.animateItemPlacement()
                            ) {
                                Card(
                                    onClick = { onOpenPatient(p.id) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = p.name.ifBlank { "Unnamed patient" },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (p.mrdNo.isNotBlank()) {
                                                Text(
                                                    text = "MRD: ${p.mrdNo}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                SuggestionChip(
                                                    onClick = {},
                                                    icon = {
                                                        Icon(
                                                            Icons.Filled.Favorite,
                                                            contentDescription = null,
                                                            tint = pathwayColor(row.pathway),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    },
                                                    label = {
                                                        Text(
                                                            row.pathway ?: "Pathway not set",
                                                            color = pathwayColor(row.pathway)
                                                        )
                                                    }
                                                )
                                                if (p.isCompleted) {
                                                    SuggestionChip(
                                                        onClick = {},
                                                        icon = {
                                                            Icon(
                                                                Icons.Filled.CheckCircle,
                                                                contentDescription = null,
                                                                tint = Color(0xFF2E7D32),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        },
                                                        label = {
                                                            Text(
                                                                "Completed",
                                                                color = Color(0xFF2E7D32)
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                LinearProgressIndicator(
                                                    progress = {
                                                        if (row.total > 0) row.answered / row.total.toFloat() else 0f
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "${row.answered}/${row.total}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Updated ${dateFmt.format(Date(p.updatedAt))}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { pendingDelete = p }) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Delete patient",
                                                tint = MaterialTheme.colorScheme.error
                                            )
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

    pendingImport?.let { parsed ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissImport() },
            title = { Text("Import backup?") },
            text = {
                Text(
                    "Import ${parsed.patients.size} patients and " +
                        "${parsed.forms.size} assessments? " +
                        "This merges with your existing data."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport() }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissImport() }) { Text("Cancel") }
            }
        )
    }

    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete patient?") },
            text = {
                Text("This permanently deletes ${p.name.ifBlank { "this patient" }} and their assessment form. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    removingId = p.id
                    scope.launch {
                        delay(320)
                        viewModel.deletePatient(p)
                        removingId = null
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}
