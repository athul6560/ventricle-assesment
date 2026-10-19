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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventricle.assessment.data.local.PatientEntity
import com.ventricle.assessment.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PatientListViewModel @Inject constructor(
    private val repository: PatientRepository
) : ViewModel() {

    val patients = repository.observePatients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun newPatient(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createPatient()
            onCreated(id)
        }
    }

    fun deletePatient(patient: PatientEntity) {
        viewModelScope.launch { repository.deletePatient(patient.id) }
    }
}

private fun categoryColor(category: String) = when (category) {
    "Possible" -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
    "Difficult/Doubtful" -> androidx.compose.ui.graphics.Color(0xFFB7791F)
    "Not Possible" -> androidx.compose.ui.graphics.Color(0xFFC62828)
    else -> androidx.compose.ui.graphics.Color(0xFF616161)
}

private fun categoryIcon(category: String): ImageVector? = when (category) {
    "Possible" -> Icons.Filled.CheckCircle
    "Difficult/Doubtful" -> Icons.Filled.HelpOutline
    "Not Possible" -> Icons.Filled.Cancel
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PatientListScreen(
    onOpenPatient: (Long) -> Unit,
    viewModel: PatientListViewModel = hiltViewModel()
) {
    val patients by viewModel.patients.collectAsState()
    var pendingDelete by remember { mutableStateOf<PatientEntity?>(null) }
    var removingId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

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
                }
            )
        },
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
        if (patients.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
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
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(patients, key = { it.id }) { p ->
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
                                        if (p.category.isNotBlank()) {
                                            SuggestionChip(
                                                onClick = {},
                                                icon = {
                                                    categoryIcon(p.category)?.let {
                                                        Icon(
                                                            it,
                                                            contentDescription = null,
                                                            tint = categoryColor(p.category),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                },
                                                label = {
                                                    Text(
                                                        p.category,
                                                        color = categoryColor(p.category)
                                                    )
                                                }
                                            )
                                        }
                                        Text(
                                            text = "Updated ${dateFmt.format(Date(p.updatedAt))}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                    }
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
