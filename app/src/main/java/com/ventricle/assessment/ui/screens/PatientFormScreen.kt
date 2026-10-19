package com.ventricle.assessment.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventricle.assessment.data.local.AssessmentFormEntity
import com.ventricle.assessment.data.local.PatientEntity
import com.ventricle.assessment.data.repository.PatientRepository
import com.ventricle.assessment.pdf.PdfExporter
import com.ventricle.assessment.ui.components.ApproachSelector
import com.ventricle.assessment.ui.components.ChoiceChips
import com.ventricle.assessment.ui.components.LikertLegend
import com.ventricle.assessment.ui.components.LikertRow
import com.ventricle.assessment.ui.components.NumberField
import com.ventricle.assessment.ui.components.PlanField
import com.ventricle.assessment.ui.components.SectionHeader
import com.ventricle.assessment.ui.components.TriLikertQuestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun Double.cleanString(): String =
    if (this % 1.0 == 0.0) this.toLong().toString() else this.toString()

private fun triText(tri: String?): String = when (tri) {
    "YES" -> "Yes"
    "NO" -> "No"
    "UNCERTAIN" -> "Uncertain"
    else -> "—"
}

@HiltViewModel
class PatientFormViewModel @Inject constructor(
    private val repository: PatientRepository,
    private val pdfExporter: PdfExporter,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val patientId: Long = savedStateHandle.get<Long>("patientId") ?: 0L

    val patient = repository.observePatient(patientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val form = repository.observeForm(patientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updatePatient(transform: (PatientEntity) -> PatientEntity) {
        viewModelScope.launch {
            val current = repository.getPatient(patientId) ?: return@launch
            repository.savePatient(transform(current))
        }
    }

    fun updateForm(transform: (AssessmentFormEntity) -> AssessmentFormEntity) {
        viewModelScope.launch {
            val current = repository.getForm(patientId)
                ?: AssessmentFormEntity(patientId = patientId)
            repository.saveForm(transform(current))
        }
    }

    suspend fun exportPdf(): Uri? {
        val p = repository.getPatient(patientId) ?: return null
        val f = repository.getForm(patientId) ?: AssessmentFormEntity(patientId = patientId)
        return pdfExporter.export(p, f)
    }
}

private val STEPS = listOf("Patient", "Primary 1°", "Secondary 2°", "Post-Op", "Review")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientFormScreen(
    onBack: () -> Unit,
    viewModel: PatientFormViewModel = hiltViewModel()
) {
    val patient by viewModel.patient.collectAsState()
    val formState by viewModel.form.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun sharePdf() {
        scope.launch {
            val uri = viewModel.exportPdf() ?: return@launch
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export assessment PDF"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        patient?.name?.ifBlank { "New patient" } ?: "New patient",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (step == 4) {
                        IconButton(onClick = ::sharePdf) {
                            Icon(Icons.Filled.Share, contentDescription = "Export PDF")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = { if (step > 0) step-- },
                    enabled = step > 0
                ) { Text("Previous") }
                Button(onClick = { if (step < 4) step++ else onBack() }) {
                    Text(if (step < 4) "Next" else "Done")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = step, edgePadding = 8.dp) {
                STEPS.forEachIndexed { i, title ->
                    Tab(
                        selected = step == i,
                        onClick = { step = i },
                        text = { Text(title) }
                    )
                }
            }
            val p = patient
            if (p == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            } else {
                val f = formState ?: AssessmentFormEntity(patientId = p.id)
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (step) {
                        0 -> PatientInfoStep(viewModel, p)
                        1 -> PrimaryStep(viewModel, f)
                        2 -> SecondaryStep(viewModel, f)
                        3 -> PostSurgicalStep(viewModel, f)
                        4 -> ReviewStep(p, f, onExport = ::sharePdf)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 1: Patient info
// ---------------------------------------------------------------------------

@Composable
private fun PatientInfoStep(vm: PatientFormViewModel, p: PatientEntity) {
    SectionHeader("Patient Information")
    BoundText("Name of the patient", p.name) { v -> vm.updatePatient { it.copy(name = v) } }
    BoundText("MRD No", p.mrdNo) { v -> vm.updatePatient { it.copy(mrdNo = v) } }
    Text("Gender", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    ChoiceChips(
        options = listOf("Male" to "Male", "Female" to "Female"),
        selected = p.gender,
        onSelect = { v -> vm.updatePatient { it.copy(gender = v) } }
    )
    NumberField(
        label = "Age", value = p.age?.toString() ?: "",
        onChange = { v -> vm.updatePatient { it.copy(age = v.toIntOrNull()) } }
    )
    BoundText("Diagnosis", p.diagnosis) { v -> vm.updatePatient { it.copy(diagnosis = v) } }
    BoundText("Date of procedure (yyyy-MM-dd)", p.procedureDate) { v ->
        vm.updatePatient { it.copy(procedureDate = v) }
    }
    BoundText("Date of discharge (yyyy-MM-dd)", p.dischargeDate) { v ->
        vm.updatePatient { it.copy(dischargeDate = v) }
    }
    Text("Category", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    ChoiceChips(
        options = listOf(
            "Possible" to "Possible",
            "Difficult/Doubtful" to "Difficult/Doubtful",
            "Not Possible" to "Not Possible"
        ),
        selected = p.category,
        onSelect = { v -> vm.updatePatient { it.copy(category = v) } }
    )
}

@Composable
private fun BoundText(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()
    )
}

// ---------------------------------------------------------------------------
// Step 2: Primary (1°) planning
// ---------------------------------------------------------------------------

@Composable
private fun PrimaryStep(vm: PatientFormViewModel, f: AssessmentFormEntity) {
    SectionHeader("Primary Level (1°) Planning", "Modality used: Echocardiography, CT-Scan/MRI-Scan")
    LikertLegend()
    TriLikertQuestion(
        number = "1",
        text = "Are the ventricles adequate / suitable for a biventricular repair?",
        tri = f.pVentriclesAdequate, onTri = { v -> vm.updateForm { it.copy(pVentriclesAdequate = v) } },
        likert = f.pVentriclesAdequateLikert, onLikert = { v -> vm.updateForm { it.copy(pVentriclesAdequateLikert = v) } }
    )
    SectionHeader("2. Routability Concern")
    TriLikertQuestion(
        number = "a",
        text = "Are the margins for baffling within reach or accessible?",
        tri = f.pMarginsReachable, onTri = { v -> vm.updateForm { it.copy(pMarginsReachable = v) } },
        likert = f.pMarginsReachableLikert, onLikert = { v -> vm.updateForm { it.copy(pMarginsReachableLikert = v) } }
    )
    TriLikertQuestion(
        number = "b",
        text = "Does baffling hinder tricuspid inflow?",
        tri = f.pHinderTricuspid, onTri = { v -> vm.updateForm { it.copy(pHinderTricuspid = v) } },
        likert = f.pHinderTricuspidLikert, onLikert = { v -> vm.updateForm { it.copy(pHinderTricuspidLikert = v) } }
    )
    NumberField(
        label = "c. Dimension of the simulated baffle — length",
        value = f.pBaffleLengthMm?.cleanString() ?: "",
        onChange = { v -> vm.updateForm { it.copy(pBaffleLengthMm = v.toDoubleOrNull()) } },
        suffix = "mm"
    )
    ApproachSelector(
        atriotomy = f.pApproachAtriotomy,
        ventriculotomy = f.pApproachVentriculotomy,
        vessels = f.pApproachVessels,
        onChange = { a, v, s ->
            vm.updateForm { it.copy(pApproachAtriotomy = a, pApproachVentriculotomy = v, pApproachVessels = s) }
        }
    )
    TriLikertQuestion(
        number = "e",
        text = "Is the VSD size sufficient for effective routing?",
        tri = f.pVsdSufficient, onTri = { v -> vm.updateForm { it.copy(pVsdSufficient = v) } },
        likert = f.pVsdSufficientLikert, onLikert = { v -> vm.updateForm { it.copy(pVsdSufficientLikert = v) } }
    )
    if (f.pVsdSufficient == "NO") {
        BoundText("In which direction can the VSD be enlarged?", f.pVsdEnlargeDirection) { v ->
            vm.updateForm { it.copy(pVsdEnlargeDirection = v) }
        }
    }
    TriLikertQuestion(
        number = "3",
        text = "Is there a requirement for a conduit?",
        tri = f.pConduitRequired, onTri = { v -> vm.updateForm { it.copy(pConduitRequired = v) } },
        likert = f.pConduitRequiredLikert, onLikert = { v -> vm.updateForm { it.copy(pConduitRequiredLikert = v) } }
    )
    PlanField(value = f.pPlan, onChange = { v -> vm.updateForm { it.copy(pPlan = v) } })
}

// ---------------------------------------------------------------------------
// Step 3: Secondary (2°) planning
// ---------------------------------------------------------------------------

@Composable
private fun SecondaryStep(vm: PatientFormViewModel, f: AssessmentFormEntity) {
    SectionHeader("Secondary Level (2°) Planning (VR/3DP)", "Modality used: 3D Recon of CT-Scan/MRI-Scan")
    LikertLegend()
    BoundText("1. Redo-sternotomy assessment", f.sRedoSternotomy) { v ->
        vm.updateForm { it.copy(sRedoSternotomy = v) }
    }
    TriLikertQuestion(
        number = "2",
        text = "Are the ventricles adequate / suitable for a biventricular repair?",
        tri = f.sVentriclesAdequate, onTri = { v -> vm.updateForm { it.copy(sVentriclesAdequate = v) } },
        likert = f.sVentriclesAdequateLikert, onLikert = { v -> vm.updateForm { it.copy(sVentriclesAdequateLikert = v) } }
    )
    SectionHeader("3. Routability")
    TriLikertQuestion(
        number = "a",
        text = "Patch prediction",
        tri = f.sPatchPrediction, onTri = { v -> vm.updateForm { it.copy(sPatchPrediction = v) } },
        likert = f.sPatchPredictionLikert, onLikert = { v -> vm.updateForm { it.copy(sPatchPredictionLikert = v) } }
    )
    if (f.sPatchPrediction == "YES") {
        Text("Dimension of the simulated baffle (mm)", fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium)
        NumberField("Length", f.sBaffleLengthMm?.cleanString() ?: "",
            { v -> vm.updateForm { it.copy(sBaffleLengthMm = v.toDoubleOrNull()) } }, "mm")
        NumberField("Breadth-1 (near VSD)", f.sBreadth1Mm?.cleanString() ?: "",
            { v -> vm.updateForm { it.copy(sBreadth1Mm = v.toDoubleOrNull()) } }, "mm")
        NumberField("Breadth-2 (mid-level)", f.sBreadth2Mm?.cleanString() ?: "",
            { v -> vm.updateForm { it.copy(sBreadth2Mm = v.toDoubleOrNull()) } }, "mm")
        NumberField("Breadth-3 (near Ao)", f.sBreadth3Mm?.cleanString() ?: "",
            { v -> vm.updateForm { it.copy(sBreadth3Mm = v.toDoubleOrNull()) } }, "mm")
    }
    TriLikertQuestion(
        number = "b",
        text = "Are the margins for baffling within reach or accessible?",
        tri = f.sMarginsReachable, onTri = { v -> vm.updateForm { it.copy(sMarginsReachable = v) } },
        likert = f.sMarginsReachableLikert, onLikert = { v -> vm.updateForm { it.copy(sMarginsReachableLikert = v) } }
    )
    TriLikertQuestion(
        number = "c",
        text = "Does baffling hinder tricuspid inflow?",
        tri = f.sHinderTricuspid, onTri = { v -> vm.updateForm { it.copy(sHinderTricuspid = v) } },
        likert = f.sHinderTricuspidLikert, onLikert = { v -> vm.updateForm { it.copy(sHinderTricuspidLikert = v) } }
    )
    ApproachSelector(
        atriotomy = f.sApproachAtriotomy,
        ventriculotomy = f.sApproachVentriculotomy,
        vessels = f.sApproachVessels,
        onChange = { a, v, s ->
            vm.updateForm { it.copy(sApproachAtriotomy = a, sApproachVentriculotomy = v, sApproachVessels = s) }
        }
    )
    TriLikertQuestion(
        number = "e",
        text = "Is the VSD size sufficient for effective routing?",
        tri = f.sVsdSufficient, onTri = { v -> vm.updateForm { it.copy(sVsdSufficient = v) } },
        likert = f.sVsdSufficientLikert, onLikert = { v -> vm.updateForm { it.copy(sVsdSufficientLikert = v) } }
    )
    if (f.sVsdSufficient == "NO") {
        BoundText("In which direction can the VSD be enlarged?", f.sVsdEnlargeDirection) { v ->
            vm.updateForm { it.copy(sVsdEnlargeDirection = v) }
        }
    }
    TriLikertQuestion(
        number = "f",
        text = "Is it necessary to remove the conal septum?",
        tri = f.sRemoveConalSeptum, onTri = { v -> vm.updateForm { it.copy(sRemoveConalSeptum = v) } },
        likert = f.sRemoveConalSeptumLikert, onLikert = { v -> vm.updateForm { it.copy(sRemoveConalSeptumLikert = v) } }
    )
    TriLikertQuestion(
        number = "4",
        text = "Is there a requirement for a conduit?",
        tri = f.sConduitRequired, onTri = { v -> vm.updateForm { it.copy(sConduitRequired = v) } },
        likert = f.sConduitRequiredLikert, onLikert = { v -> vm.updateForm { it.copy(sConduitRequiredLikert = v) } }
    )
    PlanField(value = f.sPlan, onChange = { v -> vm.updateForm { it.copy(sPlan = v) } })
}

// ---------------------------------------------------------------------------
// Step 4: Post-surgical
// ---------------------------------------------------------------------------

@Composable
private fun PostSurgicalStep(vm: PatientFormViewModel, f: AssessmentFormEntity) {
    SectionHeader("Post-Surgical Level Discussion & Validation")
    LikertLegend()
    Text("1. Was the plan same or different?", fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium)
    ChoiceChips(
        options = listOf("Same" to "Same", "Different" to "Different"),
        selected = f.psPlanSame,
        onSelect = { v -> vm.updateForm { it.copy(psPlanSame = v) } }
    )
    if (f.psPlanSame == "Different") {
        BoundText("If different, why?", f.psPlanWhyDifferent) { v ->
            vm.updateForm { it.copy(psPlanWhyDifferent = v) }
        }
    }
    BoundText("2. Redo-sternotomy assessment", f.psRedoSternotomy) { v ->
        vm.updateForm { it.copy(psRedoSternotomy = v) }
    }
    TriLikertQuestion(
        number = "3",
        text = "Are the ventricles adequate / suitable for a biventricular repair?",
        tri = f.psVentriclesAdequate, onTri = { v -> vm.updateForm { it.copy(psVentriclesAdequate = v) } },
        likert = f.psVentriclesAdequateLikert, onLikert = { v -> vm.updateForm { it.copy(psVentriclesAdequateLikert = v) } }
    )
    Text("4. Surgical baffle dimensions (mm)", fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium)
    NumberField("Length", f.psBaffleLengthMm?.cleanString() ?: "",
        { v -> vm.updateForm { it.copy(psBaffleLengthMm = v.toDoubleOrNull()) } }, "mm")
    NumberField("Breadth-1 (near VSD)", f.psBreadth1Mm?.cleanString() ?: "",
        { v -> vm.updateForm { it.copy(psBreadth1Mm = v.toDoubleOrNull()) } }, "mm")
    NumberField("Breadth-2 (mid-level)", f.psBreadth2Mm?.cleanString() ?: "",
        { v -> vm.updateForm { it.copy(psBreadth2Mm = v.toDoubleOrNull()) } }, "mm")
    NumberField("Breadth-3 (near Ao)", f.psBreadth3Mm?.cleanString() ?: "",
        { v -> vm.updateForm { it.copy(psBreadth3Mm = v.toDoubleOrNull()) } }, "mm")
    TriLikertQuestion(
        number = "5",
        text = "Does baffling hinder tricuspid inflow?",
        tri = f.psHinderTricuspid, onTri = { v -> vm.updateForm { it.copy(psHinderTricuspid = v) } },
        likert = f.psHinderTricuspidLikert, onLikert = { v -> vm.updateForm { it.copy(psHinderTricuspidLikert = v) } }
    )
    TriLikertQuestion(
        number = "6",
        text = "Are the margins for baffling within reach or accessible?",
        tri = f.psMarginsReachable, onTri = { v -> vm.updateForm { it.copy(psMarginsReachable = v) } },
        likert = f.psMarginsReachableLikert, onLikert = { v -> vm.updateForm { it.copy(psMarginsReachableLikert = v) } }
    )
    TriLikertQuestion(
        number = "7",
        text = "Is there a requirement for a conduit?",
        tri = f.psConduitRequired, onTri = { v -> vm.updateForm { it.copy(psConduitRequired = v) } },
        likert = f.psConduitRequiredLikert, onLikert = { v -> vm.updateForm { it.copy(psConduitRequiredLikert = v) } }
    )
    TriLikertQuestion(
        number = "8",
        text = "Is it necessary to remove the conal septum?",
        tri = f.psRemoveConalSeptum, onTri = { v -> vm.updateForm { it.copy(psRemoveConalSeptum = v) } },
        likert = f.psRemoveConalSeptumLikert, onLikert = { v -> vm.updateForm { it.copy(psRemoveConalSeptumLikert = v) } }
    )
    TriLikertQuestion(
        number = "9",
        text = "Is the VSD size sufficient for effective routing?",
        tri = f.psVsdSufficient, onTri = { v -> vm.updateForm { it.copy(psVsdSufficient = v) } },
        likert = f.psVsdSufficientLikert, onLikert = { v -> vm.updateForm { it.copy(psVsdSufficientLikert = v) } }
    )
    if (f.psVsdSufficient == "NO") {
        BoundText("In which direction can the VSD be enlarged?", f.psVsdEnlargeDirection) { v ->
            vm.updateForm { it.copy(psVsdEnlargeDirection = v) }
        }
    }
    Text("10. Surgical approach", fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium)
    ApproachSelector(
        atriotomy = f.psApproachAtriotomy,
        ventriculotomy = f.psApproachVentriculotomy,
        vessels = f.psApproachVessels,
        onChange = { a, v, s ->
            vm.updateForm { it.copy(psApproachAtriotomy = a, psApproachVentriculotomy = v, psApproachVessels = s) }
        }
    )
    Text("11. Significance of VR/3DP-based planning (Likert 1–5)", fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium)
    LikertLabeledRow("Decision making of the procedure", f.psLikertDecision) { v ->
        vm.updateForm { it.copy(psLikertDecision = v) }
    }
    LikertLabeledRow("Execution of the procedure", f.psLikertExecution) { v ->
        vm.updateForm { it.copy(psLikertExecution = v) }
    }
    LikertLabeledRow("Anticipating technical challenges", f.psLikertChallenges) { v ->
        vm.updateForm { it.copy(psLikertChallenges = v) }
    }
    LikertLabeledRow("Optimizing the surgical procedure", f.psLikertOptimizing) { v ->
        vm.updateForm { it.copy(psLikertOptimizing = v) }
    }
    NumberField("12. CPB time", f.psCpbMinutes?.toString() ?: "",
        { v -> vm.updateForm { it.copy(psCpbMinutes = v.toIntOrNull()) } }, "mins")
    NumberField("13. ACC time", f.psAccMinutes?.toString() ?: "",
        { v -> vm.updateForm { it.copy(psAccMinutes = v.toIntOrNull()) } }, "mins")
}

@Composable
private fun LikertLabeledRow(label: String, selected: Int?, onSelect: (Int?) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        LikertRow(selected = selected, onSelect = onSelect, label = "")
    }
}

// ---------------------------------------------------------------------------
// Step 5: Review + export
// ---------------------------------------------------------------------------

@Composable
private fun ReviewItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ReviewTri(label: String, tri: String?, likert: Int?) {
    ReviewItem(label, "${triText(tri)}   •   ${likert?.let { "Likert: $it" } ?: "—"}")
}

private fun approachText(a: Boolean, v: Boolean, s: Boolean): String {
    val parts = mutableListOf<String>()
    if (a) parts += "Atriotomy"
    if (v) parts += "Ventriculotomy"
    if (s) parts += "Vessels"
    return parts.ifEmpty { listOf("—") }.joinToString(", ")
}

private fun mmText(v: Double?): String = v?.cleanString()?.let { "$it mm" } ?: "—"

@Composable
private fun ReviewStep(p: PatientEntity, f: AssessmentFormEntity, onExport: () -> Unit) {
    SectionHeader("Review", "Check everything, then export the assessment as PDF.")
    Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
        Text("Export PDF")
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    SectionHeader("Patient Information")
    ReviewItem("Name", p.name)
    ReviewItem("MRD No", p.mrdNo)
    ReviewItem("Gender", p.gender)
    ReviewItem("Age", p.age?.toString() ?: "")
    ReviewItem("Diagnosis", p.diagnosis)
    ReviewItem("Date of procedure", p.procedureDate)
    ReviewItem("Date of discharge", p.dischargeDate)
    ReviewItem("Category", p.category)

    SectionHeader("Primary (1°) Planning")
    ReviewTri("1. Ventricles adequate for biventricular repair", f.pVentriclesAdequate, f.pVentriclesAdequateLikert)
    ReviewTri("2a. Margins for baffling within reach", f.pMarginsReachable, f.pMarginsReachableLikert)
    ReviewTri("2b. Baffling hinders tricuspid inflow", f.pHinderTricuspid, f.pHinderTricuspidLikert)
    ReviewItem("2c. Simulated baffle length", mmText(f.pBaffleLengthMm))
    ReviewItem("2d. Surgical approach", approachText(f.pApproachAtriotomy, f.pApproachVentriculotomy, f.pApproachVessels))
    ReviewTri("2e. VSD size sufficient", f.pVsdSufficient, f.pVsdSufficientLikert)
    if (f.pVsdSufficient == "NO") ReviewItem("VSD enlarge direction", f.pVsdEnlargeDirection)
    ReviewTri("3. Conduit required", f.pConduitRequired, f.pConduitRequiredLikert)
    ReviewItem("PLAN", f.pPlan)

    SectionHeader("Secondary (2°) Planning (VR/3DP)")
    ReviewItem("1. Redo-sternotomy assessment", f.sRedoSternotomy)
    ReviewTri("2. Ventricles adequate for biventricular repair", f.sVentriclesAdequate, f.sVentriclesAdequateLikert)
    ReviewTri("3a. Patch prediction", f.sPatchPrediction, f.sPatchPredictionLikert)
    if (f.sPatchPrediction == "YES") {
        ReviewItem("Baffle length", mmText(f.sBaffleLengthMm))
        ReviewItem("Breadth-1 (near VSD)", mmText(f.sBreadth1Mm))
        ReviewItem("Breadth-2 (mid-level)", mmText(f.sBreadth2Mm))
        ReviewItem("Breadth-3 (near Ao)", mmText(f.sBreadth3Mm))
    }
    ReviewTri("3b. Margins for baffling within reach", f.sMarginsReachable, f.sMarginsReachableLikert)
    ReviewTri("3c. Baffling hinders tricuspid inflow", f.sHinderTricuspid, f.sHinderTricuspidLikert)
    ReviewItem("3d. Surgical approach", approachText(f.sApproachAtriotomy, f.sApproachVentriculotomy, f.sApproachVessels))
    ReviewTri("3e. VSD size sufficient", f.sVsdSufficient, f.sVsdSufficientLikert)
    if (f.sVsdSufficient == "NO") ReviewItem("VSD enlarge direction", f.sVsdEnlargeDirection)
    ReviewTri("3f. Remove conal septum", f.sRemoveConalSeptum, f.sRemoveConalSeptumLikert)
    ReviewTri("4. Conduit required", f.sConduitRequired, f.sConduitRequiredLikert)
    ReviewItem("PLAN", f.sPlan)

    SectionHeader("Post-Surgical Validation")
    ReviewItem("1. Plan same or different", f.psPlanSame)
    if (f.psPlanSame == "Different") ReviewItem("Why different", f.psPlanWhyDifferent)
    ReviewItem("2. Redo-sternotomy assessment", f.psRedoSternotomy)
    ReviewTri("3. Ventricles adequate for biventricular repair", f.psVentriclesAdequate, f.psVentriclesAdequateLikert)
    ReviewItem("4. Baffle length", mmText(f.psBaffleLengthMm))
    ReviewItem("Breadth-1 (near VSD)", mmText(f.psBreadth1Mm))
    ReviewItem("Breadth-2 (mid-level)", mmText(f.psBreadth2Mm))
    ReviewItem("Breadth-3 (near Ao)", mmText(f.psBreadth3Mm))
    ReviewTri("5. Baffling hinders tricuspid inflow", f.psHinderTricuspid, f.psHinderTricuspidLikert)
    ReviewTri("6. Margins for baffling within reach", f.psMarginsReachable, f.psMarginsReachableLikert)
    ReviewTri("7. Conduit required", f.psConduitRequired, f.psConduitRequiredLikert)
    ReviewTri("8. Remove conal septum", f.psRemoveConalSeptum, f.psRemoveConalSeptumLikert)
    ReviewTri("9. VSD size sufficient", f.psVsdSufficient, f.psVsdSufficientLikert)
    if (f.psVsdSufficient == "NO") ReviewItem("VSD enlarge direction", f.psVsdEnlargeDirection)
    ReviewItem("10. Surgical approach", approachText(f.psApproachAtriotomy, f.psApproachVentriculotomy, f.psApproachVessels))
    ReviewItem("11a. VR/3DP significance — decision making", f.psLikertDecision?.toString() ?: "")
    ReviewItem("11b. VR/3DP significance — execution", f.psLikertExecution?.toString() ?: "")
    ReviewItem("11c. VR/3DP significance — anticipating challenges", f.psLikertChallenges?.toString() ?: "")
    ReviewItem("11d. VR/3DP significance — optimizing procedure", f.psLikertOptimizing?.toString() ?: "")
    ReviewItem("12. CPB time", f.psCpbMinutes?.let { "$it mins" } ?: "")
    ReviewItem("13. ACC time", f.psAccMinutes?.let { "$it mins" } ?: "")

    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
        Text("Export PDF")
    }
}
