package com.ventricle.assessment.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

const val LIKERT_LEGEND =
    "Likert scale: 1 = Strongly Disagree, 2 = Disagree, 3 = Neutral, 4 = Agree, 5 = Strongly Agree"

// ---------------------------------------------------------------------------
// Staggered entrance: items fade + slide in one after another on first
// composition. Wrap step content in StaggerHost; each StaggerItem picks up
// the next index automatically. Cheap: one-shot, ~45ms stagger.
// ---------------------------------------------------------------------------

private class StaggerScope {
    var index = 0
}

private val LocalStagger = compositionLocalOf<StaggerScope?> { null }

@Composable
fun StaggerHost(content: @Composable () -> Unit) {
    val scope = remember { StaggerScope() }
    CompositionLocalProvider(LocalStagger provides scope, content = content)
}

@Composable
fun StaggerItem(content: @Composable () -> Unit) {
    val scope = LocalStagger.current
    if (scope == null) {
        content()
        return
    }
    val index = remember { scope.index++ }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index * 45).toLong())
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(280),
        label = "stagger_alpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 26f,
        animationSpec = tween(280),
        label = "stagger_dy"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            translationY = offsetY
        }
    ) { content() }
}

// ---------------------------------------------------------------------------
// Animated selection chip: springy scale pop on select + FilterChip's own
// color animation.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimatedSelectChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors()
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.07f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "chip_scale"
    )
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        leadingIcon = leadingIcon,
        colors = colors,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

@Composable
fun LikertLegend() {
    StaggerItem {
        Text(
            text = LIKERT_LEGEND,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, icon: ImageVector? = null) {
    StaggerItem {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column {
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
    }
}

/** Single-select chips (tap again to clear). */
@Composable
fun ChoiceChips(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    StaggerItem {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                AnimatedSelectChip(
                    selected = selected == value,
                    onClick = { onSelect(if (selected == value) "" else value) },
                    label = { Text(label) }
                )
            }
        }
    }
}

private val TRI_OPTIONS = listOf(
    Triple("YES", "Yes", Icons.Filled.Check),
    Triple("NO", "No", Icons.Filled.Close),
    Triple("UNCERTAIN", "Uncertain", Icons.Filled.Help)
)

/** Selected-state container colors for the trichotomous chips: green / red / amber. */
private val TRI_SELECTED_COLORS = mapOf(
    "YES" to Color(0xFF2E7D32),
    "NO" to Color(0xFFC62828),
    "UNCERTAIN" to Color(0xFFB7791F)
)

/**
 * One proforma question: trichotomous Yes/No/Uncertain chips + Likert 1-5 selector.
 * Tri-state values: "YES" / "NO" / "UNCERTAIN" / null.
 */
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
    StaggerItem {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(280)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "$number. $text",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TRI_OPTIONS.forEach { (v, label, icon) ->
                        AnimatedSelectChip(
                            selected = tri == v,
                            onClick = { onTri(if (tri == v) null else v) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TRI_SELECTED_COLORS.getValue(v),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                LikertRow(selected = likert, onSelect = onLikert)
            }
        }
    }
}

/** Likert 1–5 selected chip color: red → amber → green scale. */
private fun likertSelectedColor(v: Int): Color = when (v) {
    1 -> Color(0xFFD32F2F)
    2 -> Color(0xFFF57C02)
    3 -> Color(0xFF9E9E9E)
    4 -> Color(0xFF7CB342)
    else -> Color(0xFF2E7D32)
}

/** Likert 1-5 selector row (tap again to clear). */
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
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        (1..5).forEach { v ->
            AnimatedSelectChip(
                selected = selected == v,
                onClick = { onSelect(if (selected == v) null else v) },
                label = { Text(v.toString()) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = likertSelectedColor(v),
                    selectedLabelColor = Color.White
                )
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
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    StaggerItem {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
            trailingIcon = { if (suffix.isNotEmpty()) Text(suffix) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = modifier.fillMaxWidth()
        )
    }
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
    StaggerItem {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(280)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
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
    }
}

/** Multi-line free-text field for the PLAN sections. */
@Composable
fun PlanField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    StaggerItem {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text("PLAN") },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            minLines = 4,
            modifier = modifier.fillMaxWidth()
        )
    }
}
