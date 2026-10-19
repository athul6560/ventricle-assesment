package com.ventricle.assessment.ui.components

import androidx.compose.ui.graphics.Color

/** Post-op pathway options. Stored verbatim in AssessmentFormEntity.psPathway. */
val PATHWAY_OPTIONS = listOf(
    "1 ventricle pathway",
    "1.5 ventricle pathway",
    "2 ventricle pathway"
)

/** Capsule color for a pathway: blue / amber / green, gray when unset. */
fun pathwayColor(pathway: String?): Color = when (pathway) {
    "1 ventricle pathway" -> Color(0xFF1565C0)
    "1.5 ventricle pathway" -> Color(0xFFB7791F)
    "2 ventricle pathway" -> Color(0xFF2E7D32)
    else -> Color(0xFF616161)
}
