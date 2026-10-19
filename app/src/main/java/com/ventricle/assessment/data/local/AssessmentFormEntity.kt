package com.ventricle.assessment.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The full proforma for one patient: Primary (1°) planning,
 * Secondary (2°) planning (VR/3DP) and Post-Surgical validation.
 *
 * Tri-state answers are stored as "YES" / "NO" / "UNCERTAIN" (null = unanswered).
 * Likert answers are 1..5 (null = unanswered).
 * Exactly one row per patient; deleted automatically with the patient (CASCADE).
 */
@Entity(
    tableName = "assessment_forms",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["patientId"], unique = true)]
)
data class AssessmentFormEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long = 0,

    // ---- Primary (1°) Planning: Echocardiography, CT/MRI ----
    val pVentriclesAdequate: String? = null,
    val pVentriclesAdequateLikert: Int? = null,
    val pMarginsReachable: String? = null,
    val pMarginsReachableLikert: Int? = null,
    val pHinderTricuspid: String? = null,
    val pHinderTricuspidLikert: Int? = null,
    val pBaffleLengthMm: Double? = null,
    val pApproachAtriotomy: Boolean = false,
    val pApproachVentriculotomy: Boolean = false,
    val pApproachVessels: Boolean = false,
    val pVsdSufficient: String? = null,
    val pVsdSufficientLikert: Int? = null,
    val pVsdEnlargeDirection: String = "",
    val pConduitRequired: String? = null,
    val pConduitRequiredLikert: Int? = null,
    val pPlan: String = "",

    // ---- Secondary (2°) Planning (VR/3DP): 3D Recon of CT/MRI ----
    val sRedoSternotomy: String = "",
    val sVentriclesAdequate: String? = null,
    val sVentriclesAdequateLikert: Int? = null,
    val sPatchPrediction: String? = null,
    val sPatchPredictionLikert: Int? = null,
    val sBaffleLengthMm: Double? = null,
    val sBreadth1Mm: Double? = null, // near VSD
    val sBreadth2Mm: Double? = null, // mid-level
    val sBreadth3Mm: Double? = null, // near Ao
    val sMarginsReachable: String? = null,
    val sMarginsReachableLikert: Int? = null,
    val sHinderTricuspid: String? = null,
    val sHinderTricuspidLikert: Int? = null,
    val sApproachAtriotomy: Boolean = false,
    val sApproachVentriculotomy: Boolean = false,
    val sApproachVessels: Boolean = false,
    val sVsdSufficient: String? = null,
    val sVsdSufficientLikert: Int? = null,
    val sVsdEnlargeDirection: String = "",
    val sRemoveConalSeptum: String? = null,
    val sRemoveConalSeptumLikert: Int? = null,
    val sConduitRequired: String? = null,
    val sConduitRequiredLikert: Int? = null,
    val sPlan: String = "",

    // ---- Post-Surgical Level Discussion & Validation ----
    val psPlanSame: String = "", // "Same" / "Different" / ""
    val psPlanWhyDifferent: String = "",
    val psRedoSternotomy: String = "",
    val psVentriclesAdequate: String? = null,
    val psVentriclesAdequateLikert: Int? = null,
    val psBaffleLengthMm: Double? = null,
    val psBreadth1Mm: Double? = null,
    val psBreadth2Mm: Double? = null,
    val psBreadth3Mm: Double? = null,
    val psHinderTricuspid: String? = null,
    val psHinderTricuspidLikert: Int? = null,
    val psMarginsReachable: String? = null,
    val psMarginsReachableLikert: Int? = null,
    val psConduitRequired: String? = null,
    val psConduitRequiredLikert: Int? = null,
    val psRemoveConalSeptum: String? = null,
    val psRemoveConalSeptumLikert: Int? = null,
    val psVsdSufficient: String? = null,
    val psVsdSufficientLikert: Int? = null,
    val psVsdEnlargeDirection: String = "",
    val psApproachAtriotomy: Boolean = false,
    val psApproachVentriculotomy: Boolean = false,
    val psApproachVessels: Boolean = false,
    // VR/3DP significance (Likert 1-5)
    val psLikertDecision: Int? = null,
    val psLikertExecution: Int? = null,
    val psLikertChallenges: Int? = null,
    val psLikertOptimizing: Int? = null,
    val psCpbMinutes: Int? = null,
    val psAccMinutes: Int? = null,
    val psPathway: String? = null, // "1 ventricle pathway" / "1.5 ventricle pathway" / "2 ventricle pathway"

    val updatedAt: Long = System.currentTimeMillis()
)
