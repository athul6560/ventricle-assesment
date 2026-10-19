package com.ventricle.assessment.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.ventricle.assessment.data.local.AssessmentFormEntity
import com.ventricle.assessment.data.local.PatientEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Renders the completed proforma as an A4 PDF using only the framework
 * [PdfDocument] — no extra dependencies. Unanswered fields render as "—".
 */
class PdfExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PAGE_W = 595
        private const val PAGE_H = 842
        private const val MARGIN = 44f
        private const val CONTENT_W = PAGE_W - MARGIN * 2
        private const val MAX_Y = 800f
    }

    private val titlePaint = TextPaint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 17f; color = Color.BLACK
    }
    private val sectionPaint = TextPaint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 13f
        color = Color.rgb(0x1A, 0x3A, 0x5C)
    }
    private val labelPaint = TextPaint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 10.5f; color = Color.BLACK
    }
    private val bodyPaint = TextPaint().apply { textSize = 10.5f; color = Color.BLACK }
    private val smallPaint = TextPaint().apply { textSize = 9f; color = Color.rgb(0x55, 0x55, 0x55) }
    private val answerPaint = TextPaint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 10.5f
        color = Color.rgb(0x1A, 0x3A, 0x5C)
    }
    private val rulePaint = Paint().apply {
        color = Color.rgb(0x1A, 0x3A, 0x5C); strokeWidth = 1.5f
    }

    private inner class Doc(private val doc: PdfDocument) {
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var canvas = page.canvas
        var y = 56f

        fun need(h: Float) {
            if (y + h > MAX_Y) {
                doc.finishPage(page)
                page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, doc.pages.size + 1).create()
                )
                canvas = page.canvas
                y = 56f
            }
        }

        fun finish() = doc.finishPage(page)

        fun text(str: String, paint: TextPaint, indent: Float = 0f, spaceAfter: Float = 5f) {
            val layout = StaticLayout.Builder.obtain(str, 0, str.length, paint, (CONTENT_W - indent).toInt()).build()
            need(layout.height + spaceAfter)
            canvas.save()
            canvas.translate(MARGIN + indent, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + spaceAfter
        }

        fun gap(h: Float) {
            y += h
        }

        fun rule() {
            need(14f)
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rulePaint)
            y += 10f
        }

        /** "1. Question" + indented "Yes  •  Likert: 4" */
        fun qa(number: String, question: String, tri: String?, likert: Int?) {
            text("$number. $question", labelPaint, spaceAfter = 2f)
            text(triLikert(tri, likert), answerPaint, indent = 16f, spaceAfter = 7f)
        }
    }

    fun export(patient: PatientEntity, form: AssessmentFormEntity): Uri {
        val doc = PdfDocument()
        val d = Doc(doc)

        d.text("Complex Bi-Ventricular Feasibility Assessment", titlePaint, spaceAfter = 3f)
        d.text(
            "Exported ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}",
            smallPaint, spaceAfter = 4f
        )
        d.rule()

        // ---- Patient header ----
        d.text("Patient Information", sectionPaint, spaceAfter = 6f)
        val p = patient
        infoLine(d, "Name", p.name)
        infoLine(d, "MRD No", p.mrdNo)
        infoLine(d, "Gender", p.gender)
        infoLine(d, "Age", p.age?.toString() ?: "")
        infoLine(d, "Diagnosis", p.diagnosis)
        infoLine(d, "Date of Procedure", p.procedureDate)
        infoLine(d, "Date of Discharge", p.dischargeDate)
        infoLine(d, "Category", p.category)
        d.gap(4f)
        d.rule()

        val f = form

        // ---- Primary ----
        d.text("Primary Level (1°) Planning", sectionPaint, spaceAfter = 2f)
        d.text("Modality used: Echocardiography, CT-Scan/MRI-Scan", smallPaint, spaceAfter = 8f)
        d.qa("1", "Are the ventricles adequate / suitable for a biventricular repair?", f.pVentriclesAdequate, f.pVentriclesAdequateLikert)
        d.text("2. Routability Concern:", labelPaint, spaceAfter = 4f)
        d.qa("a", "Are the margins for baffling within reach or accessible?", f.pMarginsReachable, f.pMarginsReachableLikert)
        d.qa("b", "Does baffling hinder tricuspid inflow?", f.pHinderTricuspid, f.pHinderTricuspidLikert)
        d.text("c. Dimension of the simulated baffle — length: ${mm(f.pBaffleLengthMm)}", bodyPaint, indent = 16f)
        d.text("d. Surgical approach: ${approach(f.pApproachAtriotomy, f.pApproachVentriculotomy, f.pApproachVessels)}", bodyPaint, indent = 16f)
        d.qa("e", "Is the VSD size sufficient for effective routing?", f.pVsdSufficient, f.pVsdSufficientLikert)
        if (f.pVsdSufficient == "NO") {
            d.text("VSD enlarge direction: ${orDash(f.pVsdEnlargeDirection)}", bodyPaint, indent = 32f)
        }
        d.qa("3", "Is there a requirement for a conduit?", f.pConduitRequired, f.pConduitRequiredLikert)
        d.text("PLAN:", labelPaint, spaceAfter = 2f)
        d.text(orDash(f.pPlan), bodyPaint, indent = 8f, spaceAfter = 6f)
        d.rule()

        // ---- Secondary ----
        d.text("Secondary Level (2°) Planning (VR/3DP)", sectionPaint, spaceAfter = 2f)
        d.text("Modality used: 3D Recon of CT-Scan/MRI-Scan", smallPaint, spaceAfter = 8f)
        d.text("1. Redo-sternotomy assessment: ${orDash(f.sRedoSternotomy)}", bodyPaint)
        d.qa("2", "Are the ventricles adequate / suitable for a biventricular repair?", f.sVentriclesAdequate, f.sVentriclesAdequateLikert)
        d.text("3. Routability:", labelPaint, spaceAfter = 4f)
        d.qa("a", "Patch prediction", f.sPatchPrediction, f.sPatchPredictionLikert)
        if (f.sPatchPrediction == "YES") {
            d.text(
                "Simulated baffle — length: ${mm(f.sBaffleLengthMm)}, breadth-1 (near VSD): ${mm(f.sBreadth1Mm)}, " +
                    "breadth-2 (mid-level): ${mm(f.sBreadth2Mm)}, breadth-3 (near Ao): ${mm(f.sBreadth3Mm)}",
                bodyPaint, indent = 32f
            )
        }
        d.qa("b", "Are the margins for baffling within reach or accessible?", f.sMarginsReachable, f.sMarginsReachableLikert)
        d.qa("c", "Does baffling hinder tricuspid inflow?", f.sHinderTricuspid, f.sHinderTricuspidLikert)
        d.text("d. Surgical approach: ${approach(f.sApproachAtriotomy, f.sApproachVentriculotomy, f.sApproachVessels)}", bodyPaint, indent = 16f)
        d.qa("e", "Is the VSD size sufficient for effective routing?", f.sVsdSufficient, f.sVsdSufficientLikert)
        if (f.sVsdSufficient == "NO") {
            d.text("VSD enlarge direction: ${orDash(f.sVsdEnlargeDirection)}", bodyPaint, indent = 32f)
        }
        d.qa("f", "Is it necessary to remove the conal septum?", f.sRemoveConalSeptum, f.sRemoveConalSeptumLikert)
        d.qa("4", "Is there a requirement for a conduit?", f.sConduitRequired, f.sConduitRequiredLikert)
        d.text("PLAN:", labelPaint, spaceAfter = 2f)
        d.text(orDash(f.sPlan), bodyPaint, indent = 8f, spaceAfter = 6f)
        d.rule()

        // ---- Post-surgical ----
        d.text("Post-Surgical Level Discussion & Validation", sectionPaint, spaceAfter = 8f)
        d.text("Pathway: ${orDash(f.psPathway ?: "")}", bodyPaint)
        d.text("1. Was the plan same or different? ${orDash(f.psPlanSame)}", bodyPaint)
        if (f.psPlanSame == "Different") {
            d.text("Why different: ${orDash(f.psPlanWhyDifferent)}", bodyPaint, indent = 16f)
        }
        d.text("2. Redo-sternotomy assessment: ${orDash(f.psRedoSternotomy)}", bodyPaint)
        d.qa("3", "Are the ventricles adequate / suitable for a biventricular repair?", f.psVentriclesAdequate, f.psVentriclesAdequateLikert)
        d.text(
            "4. Surgical baffle dimensions — length: ${mm(f.psBaffleLengthMm)}, breadth-1 (near VSD): ${mm(f.psBreadth1Mm)}, " +
                "breadth-2 (mid-level): ${mm(f.psBreadth2Mm)}, breadth-3 (near Ao): ${mm(f.psBreadth3Mm)}",
            bodyPaint
        )
        d.qa("5", "Does baffling hinder tricuspid inflow?", f.psHinderTricuspid, f.psHinderTricuspidLikert)
        d.qa("6", "Are the margins for baffling within reach or accessible?", f.psMarginsReachable, f.psMarginsReachableLikert)
        d.qa("7", "Is there a requirement for a conduit?", f.psConduitRequired, f.psConduitRequiredLikert)
        d.qa("8", "Is it necessary to remove the conal septum?", f.psRemoveConalSeptum, f.psRemoveConalSeptumLikert)
        d.qa("9", "Is the VSD size sufficient for effective routing?", f.psVsdSufficient, f.psVsdSufficientLikert)
        if (f.psVsdSufficient == "NO") {
            d.text("VSD enlarge direction: ${orDash(f.psVsdEnlargeDirection)}", bodyPaint, indent = 32f)
        }
        d.text("10. Surgical approach: ${approach(f.psApproachAtriotomy, f.psApproachVentriculotomy, f.psApproachVessels)}", bodyPaint)
        d.gap(2f)
        d.text("11. Significance of VR/3DP-based planning (Likert 1–5):", labelPaint, spaceAfter = 4f)
        d.text("• Decision making of the procedure: ${lik(f.psLikertDecision)}", bodyPaint, indent = 16f)
        d.text("• Execution of the procedure: ${lik(f.psLikertExecution)}", bodyPaint, indent = 16f)
        d.text("• Anticipating technical challenges: ${lik(f.psLikertChallenges)}", bodyPaint, indent = 16f)
        d.text("• Optimizing the surgical procedure: ${lik(f.psLikertOptimizing)}", bodyPaint, indent = 16f)
        d.gap(2f)
        d.text("12. CPB time: ${min(f.psCpbMinutes)}", bodyPaint)
        d.text("13. ACC time: ${min(f.psAccMinutes)}", bodyPaint)

        d.finish()

        val base = p.mrdNo.ifBlank { p.name.ifBlank { "patient" } }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val file = File(context.cacheDir, "VentricleAssessment_${base}_${stamp}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // ---- helpers ----
    private fun orDash(v: String): String = v.ifBlank { "—" }

    private fun triLikert(tri: String?, likert: Int?): String {
        val t = when (tri) {
            "YES" -> "Yes"; "NO" -> "No"; "UNCERTAIN" -> "Uncertain"; else -> "—"
        }
        val l = likert?.let { "Likert: $it" } ?: "—"
        return "$t   •   $l"
    }

    private fun mm(v: Double?): String = v?.let { "$it mm" } ?: "—"

    private fun lik(v: Int?): String = v?.toString() ?: "—"

    private fun min(v: Int?): String = v?.let { "$it mins" } ?: "—"

    private fun approach(a: Boolean, v: Boolean, s: Boolean): String {
        val parts = mutableListOf<String>()
        if (a) parts += "Atriotomy"
        if (v) parts += "Ventriculotomy"
        if (s) parts += "Vessels"
        return parts.ifEmpty { listOf("—") }.joinToString(", ")
    }

    private fun infoLine(d: Doc, label: String, value: String) {
        d.text("$label: ${orDash(value)}", bodyPaint, spaceAfter = 4f)
    }
}
