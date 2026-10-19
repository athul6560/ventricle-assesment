package com.ventricle.assessment.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

const val LIKERT_LEGEND =
    "Likert scale: 1 = Strongly Disagree, 2 = Disagree, 3 = Neutral, 4 = Agree, 5 = Strongly Agree"

@Composable
fun LikertLegend() {
    Text(
        text = LIKERT_LEGEND,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Single-select chips (tap again to clear). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceChips(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(if (selected == value) "" else value) },
                label = { Text(label) }
            )
        }
    }
}

/**
 * One proforma question: trichotomous Yes/No/Uncertain chips + Likert 1-5 selector.
 * Tri-state values: "YES" / "NO" / "UNCERTAIN" / null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriLikertQuestion(
    number: String,
    text: String,
    tri: String?,
    onTri: (String?) -> Unit,
    likert: Int?,
    onLikert: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "$number. $text",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("YES" to "Yes", "NO" to "No", "UNCERTAIN" to "Uncertain").forEach { (v, label) ->
                    FilterChip(
                        selected = tri == v,
                        onClick = { onTri(if (tri == v) null else v) },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            LikertRow(selected = likert, onSelect = onLikert)
        }
    }
}

/** Likert 1-5 selector row (tap again to clear). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikertRow(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    label: String = "Likert"
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        (1..5).forEach { v ->
            FilterChip(
                selected = selected == v,
                onClick = { onSelect(if (selected == v) null else v) },
                label = { Text(v.toString()) }
            )
        }
    }
}

/** Numeric text field (digits + one dot). Value is a raw String; parse at the call site. */
@Composable
fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    suffix: String = "",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(label) },
        trailingIcon = { if (suffix.isNotEmpty()) Text(suffix) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

/** The three surgical-approach checkboxes from the proforma. */
@Composable
fun ApproachSelector(
    atriotomy: Boolean,
    ventriculotomy: Boolean,
    vessels: Boolean,
    onChange: (a: Boolean, v: Boolean, s: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Surgical approach",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        listOf(
            Triple("Atriotomy", atriotomy) { b: Boolean -> onChange(b, ventriculotomy, vessels) },
            Triple("Ventriculotomy", ventriculotomy) { b: Boolean -> onChange(atriotomy, b, vessels) },
            Triple("Vessels", vessels) { b: Boolean -> onChange(atriotomy, ventriculotomy, b) }
        ).forEach { (label, checked, set) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = set)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Multi-line free-text field for the PLAN sections. */
@Composable
fun PlanField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("PLAN") },
        minLines = 4,
        modifier = modifier.fillMaxWidth()
    )
}
