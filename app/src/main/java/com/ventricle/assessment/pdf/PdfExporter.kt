package com.ventricle.assessment.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
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
 * Renders the proforma as a professional A4 clinical report using only the
 * framework [PdfDocument] — no extra dependencies.
 *
 * Layout: coloured title band (page 1), patient summary card, then one table
 * per assessment level (Question | Response | Score) with wrapped text,
 * alternating row shading and repeated column headers across page breaks.
 * Every page carries a footer with "Page X of Y" — page counts are exact
 * because the whole layout is computed (paginated) before anything is drawn.
 */
class PdfExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PAGE_W = 595
        private const val PAGE_H = 842
        private const val MARGIN = 48f
        private const val RIGHT = PAGE_W - MARGIN // 547
        private const val CONTENT_W = RIGHT - MARGIN // 499

        private const val BAND_H = 96f
        private const val FIRST_TOP = 120f
        private const val NEXT_TOP = 64f
        private const val CONTENT_BOTTOM = 788f

        // Table columns: Question | Response | Score
        private const val COL_R_X = 333f
        private const val COL_S_X = 443f
        private const val COL_Q_TEXT_X = MARGIN + 6f // 54
        private const val COL_Q_W = 273f
        private const val COL_R_TEXT_X = COL_R_X + 6f // 339
        private const val COL_R_W = 98f
        private const val COL_S_TEXT_X = COL_S_X + 6f // 449
        private const val COL_S_W = 92f
        private const val VALUE_W = RIGHT - 6f - COL_R_TEXT_X // 202 (kv value spans Response+Score)

        private const val COL_H = 18f
        private const val SECTION_H = 26f
        private const val ROW_PAD = 6f
        private const val CARD_PAD = 14f
        private const val CARD_COL_GAP = 18f
    }

    // ---- palette ----
    private val accent = Color.rgb(0x0F, 0x76, 0x6E) // deep teal
    private val accentDark = Color.rgb(0x0B, 0x4F, 0x4A)
    private val ink = Color.rgb(0x1F, 0x29, 0x37)
    private val greyText = Color.rgb(0x64, 0x74, 0x8B)
    private val faintText = Color.rgb(0x94, 0xA3, 0xB8)
    private val colHeaderColor = Color.rgb(0x47, 0x55, 0x69)
    private val lightLine = Color.rgb(0xE2, 0xE8, 0xF0)
    private val dividerLine = Color.rgb(0xCB, 0xD5, 0xE1)
    private val cardBg = Color.rgb(0xF1, 0xF5, 0xF9)
    private val shadeBg = Color.rgb(0xF8, 0xFA, 0xFC)

    private fun tp(
        size: Float,
        color: Int,
        style: Int = Typeface.NORMAL,
        align: Paint.Align = Paint.Align.LEFT
    ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, style)
        textSize = size
        this.color = color
        textAlign = align
    }

    private fun fill(color: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun stroke(color: Int, width: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = width
        }

    private val bandTitle = tp(17f, Color.WHITE, Typeface.BOLD)
    private val bandSub = tp(11f, Color.argb(215, 255, 255, 255))
    private val bandDateLabel = tp(8f, Color.argb(200, 255, 255, 255), Typeface.BOLD, Paint.Align.RIGHT)
    private val bandDate = tp(11f, Color.WHITE, Typeface.BOLD, Paint.Align.RIGHT)
    private val miniLeft = tp(9f, colHeaderColor, Typeface.BOLD)
    private val miniRight = tp(9f, greyText, Typeface.NORMAL, Paint.Align.RIGHT)
    private val cardTitle = tp(11f, accentDark, Typeface.BOLD)
    private val cardLabel = tp(8.5f, greyText, Typeface.BOLD)
    private val cardValue = tp(10.5f, ink)
    private val sectionText = tp(12f, Color.WHITE, Typeface.BOLD)
    private val captionPaint = tp(9f, greyText, Typeface.ITALIC)
    private val colHeaderPaint = tp(8.5f, colHeaderColor, Typeface.BOLD)
    private val qPaint = tp(10f, ink)
    private val qBoldPaint = tp(10f, ink, Typeface.BOLD)
    private val respPaint = tp(10f, ink, Typeface.BOLD)
    private val subPaint = tp(10.5f, accentDark, Typeface.BOLD)
    private val noteLabelPaint = tp(10f, accentDark, Typeface.BOLD)
    private val notePaint = tp(10f, ink)
    private val emptyPaint = tp(10.5f, greyText, Typeface.ITALIC)
    private val footerPaint = tp(8f, faintText)
    private val footerRightPaint = tp(8f, faintText, Typeface.NORMAL, Paint.Align.RIGHT)

    private val accentFill = fill(accent)
    private val accentDarkFill = fill(accentDark)
    private val cardFill = fill(cardBg)
    private val shadeFill = fill(shadeBg)
    private val lineStroke = stroke(lightLine, 0.7f)
    private val dividerStroke = stroke(dividerLine, 0.8f)
    private val footerStroke = stroke(lightLine, 0.8f)

    // ------------------------------------------------------------------
    // Content model
    // ------------------------------------------------------------------
    private sealed interface Row

    /** response == null leaves the Response cell blank (score-only rows). */
    private data class QaRow(val question: String, val response: String?, val score: String?) : Row
    private data class KvRow(val label: String, val value: String, val indent: Boolean = false) : Row
    private data class SubRow(val text: String) : Row
    private data class NoteRow(val label: String, val text: String) : Row

    private data class Section(val title: String, val caption: String?, val rows: List<Row>)

    private data class CardCell(val label: String, val value: String, val span: Boolean = false)
    private class MeasuredCell(val cell: CardCell, val layout: StaticLayout, val x: Float)

    // ------------------------------------------------------------------
    // Public API (unchanged)
    // ------------------------------------------------------------------
    fun export(patient: PatientEntity, form: AssessmentFormEntity): Uri {
        val generated = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())

        val p = Paginator()
        p.summaryCard(summaryVisualRows(patient, form))
        val sections = buildSections(form)
        if (sections.isEmpty()) {
            p.emptyNote("No assessment data recorded yet.")
        } else {
            for (section in sections) {
                p.sectionHeader(section.title, section.caption)
                section.rows.forEachIndexed { index, row ->
                    when (row) {
                        is QaRow -> p.qaRow(row)
                        is KvRow -> p.kvRow(row)
                        is SubRow -> p.subHeader(row.text)
                        is NoteRow -> {
                            p.beginNoteBlock()
                            p.noteRow(row)
                            if (index < section.rows.lastIndex) p.endNoteBlock()
                        }
                    }
                }
                p.endSection()
            }
        }

        val doc = PdfDocument()
        val total = p.pages.size
        p.pages.forEachIndexed { idx, pageOps ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, idx + 1).create())
            val c = page.canvas
            if (idx == 0) drawBand(c, generated) else drawMiniHeader(c, patient)
            pageOps.forEach { it(c) }
            drawFooter(c, idx + 1, total)
            doc.finishPage(page)
        }

        val safeName = patient.name.trim()
            .replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
            .ifBlank { "Patient" }
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val file = File(context.cacheDir, "VentricularRepair_${safeName}_${stamp}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // ------------------------------------------------------------------
    // Page furniture
    // ------------------------------------------------------------------
    private fun drawBand(c: Canvas, dateStr: String) {
        c.drawRect(0f, 0f, PAGE_W.toFloat(), BAND_H, accentFill)
        c.drawRect(0f, BAND_H, PAGE_W.toFloat(), BAND_H + 3f, accentDarkFill)
        c.drawText("Complex Bi-Ventricular Feasibility Assessment", MARGIN, 42f, bandTitle)
        c.drawText("Ventricular Repair Assessment", MARGIN, 66f, bandSub)
        c.drawText("REPORT DATE", RIGHT, 36f, bandDateLabel)
        c.drawText(dateStr, RIGHT, 56f, bandDate)
    }

    private fun drawMiniHeader(c: Canvas, patient: PatientEntity) {
        c.drawText("Ventricular Repair Assessment", MARGIN, 40f, miniLeft)
        val who = listOf(patient.name, patient.mrdNo)
            .filter { it.isNotBlank() }
            .joinToString("  ·  ")
        if (who.isNotEmpty()) c.drawText(who, RIGHT, 40f, miniRight)
        c.drawLine(MARGIN, 48f, RIGHT, 48f, footerStroke)
    }

    private fun drawFooter(c: Canvas, page: Int, total: Int) {
        c.drawLine(MARGIN, 802f, RIGHT, 802f, footerStroke)
        c.drawText("Ventricular Repair Assessment — Confidential patient report", MARGIN, 816f, footerPaint)
        c.drawText("Page $page of $total", RIGHT, 816f, footerRightPaint)
    }

    // ------------------------------------------------------------------
    // Layout helpers
    // ------------------------------------------------------------------
    private fun layout(text: String, paint: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.12f)
            .setIncludePad(false)
            .build()

    private fun drawLayout(c: Canvas, layout: StaticLayout, x: Float, y: Float) {
        c.save()
        c.translate(x, y)
        layout.draw(c)
        c.restore()
    }

    // ------------------------------------------------------------------
    // Paginator: computes page breaks first, records draw operations.
    // ------------------------------------------------------------------
    private inner class Paginator {
        val pages = mutableListOf<MutableList<(Canvas) -> Unit>>()
        private var ops = mutableListOf<(Canvas) -> Unit>()
        var y = FIRST_TOP
        private var inTable = false
        private var shadeIndex = 0
        private var noteResumesTable = false

        init {
            pages += ops
        }

        private fun add(block: (Canvas) -> Unit) {
            ops += block
        }

        fun ensure(h: Float) {
            if (y + h > CONTENT_BOTTOM) newPage()
        }

        fun newPage() {
            ops = mutableListOf()
            pages += ops
            y = NEXT_TOP
            if (inTable) colHeader()
        }

        // ---- patient summary card ----
        fun summaryCard(visualRows: List<List<CardCell>>) {
            val innerX = MARGIN + CARD_PAD
            val innerW = CONTENT_W - CARD_PAD * 2
            val colW = (innerW - CARD_COL_GAP) / 2f
            val colX = listOf(innerX, innerX + colW + CARD_COL_GAP)

            val measuredRows = mutableListOf<Pair<List<MeasuredCell>, Float>>()
            var rowsH = 0f
            for (row in visualRows) {
                val cells = row.mapIndexed { idx, cell ->
                    val w = if (cell.span) innerW else colW
                    val x = if (cell.span) innerX else colX[idx]
                    MeasuredCell(cell, layout(cell.value, cardValue, w), x)
                }
                val h = 13f + (cells.maxOfOrNull { it.layout.height } ?: 0) + 10f
                measuredRows += cells to h
                rowsH += h
            }
            val cardH = 34f + rowsH + 8f
            ensure(cardH + 4f)
            val top = y
            add { c -> c.drawRoundRect(RectF(MARGIN, top, RIGHT, top + cardH), 10f, 10f, cardFill) }
            add { c -> c.drawText("PATIENT SUMMARY", innerX, top + 22f, cardTitle) }
            var rowY = top + 34f
            for ((cells, h) in measuredRows) {
                val ry = rowY
                for (mc in cells) {
                    val cell = mc.cell
                    val lay = mc.layout
                    val x = mc.x
                    add { c ->
                        c.drawText(cell.label.uppercase(Locale.US), x, ry + 9f, cardLabel)
                        drawLayout(c, lay, x, ry + 13f)
                    }
                }
                rowY += h
            }
            y = top + cardH + 18f
        }

        fun emptyNote(text: String) {
            val lay = layout(text, emptyPaint, CONTENT_W)
            ensure(lay.height + 8f)
            val t = y
            add { c -> drawLayout(c, lay, MARGIN, t) }
            y += lay.height + 8f
        }

        // ---- sections & tables ----
        fun sectionHeader(title: String, caption: String?) {
            inTable = false
            val need = SECTION_H + 6f + (if (caption != null) 17f else 0f) + COL_H + 32f
            ensure(need)
            val top = y
            add { c ->
                c.drawRoundRect(RectF(MARGIN, top, RIGHT, top + SECTION_H), 5f, 5f, accentFill)
                c.drawText(title, MARGIN + 12f, top + 17.5f, sectionText)
            }
            y += SECTION_H + 4f
            if (caption != null) {
                val capY = y
                add { c -> c.drawText(caption, MARGIN + 2f, capY + 10f, captionPaint) }
                y += 15f
            }
            y += 2f
            colHeader()
            inTable = true
            shadeIndex = 0
        }

        fun colHeader() {
            val top = y
            add { c ->
                c.drawText("QUESTION", COL_Q_TEXT_X, top + 12f, colHeaderPaint)
                c.drawText("RESPONSE", COL_R_TEXT_X, top + 12f, colHeaderPaint)
                c.drawText("SCORE", COL_S_TEXT_X, top + 12f, colHeaderPaint)
                c.drawLine(COL_R_X, top, COL_R_X, top + COL_H, lineStroke)
                c.drawLine(COL_S_X, top, COL_S_X, top + COL_H, lineStroke)
                c.drawLine(MARGIN, top + COL_H, RIGHT, top + COL_H, dividerStroke)
            }
            y += COL_H
        }

        fun endSection() {
            inTable = false
            y += 14f
        }

        fun qaRow(row: QaRow) {
            val qLay = layout(row.question, qPaint, COL_Q_W)
            val rLay = row.response?.let { layout(it, respPaint, COL_R_W) }
            val sLay = row.score?.let { layout(it, qPaint, COL_S_W) }
            val contentH = maxOf(qLay.height, rLay?.height ?: 0, sLay?.height ?: 0)
            val rowH = contentH + ROW_PAD * 2
            ensure(rowH)
            val top = y
            val shade = shadeIndex % 2 == 1
            add { c ->
                if (shade) c.drawRect(MARGIN, top, RIGHT, top + rowH, shadeFill)
                drawLayout(c, qLay, COL_Q_TEXT_X, top + ROW_PAD)
                if (rLay != null) drawLayout(c, rLay, COL_R_TEXT_X, top + ROW_PAD)
                if (sLay != null) drawLayout(c, sLay, COL_S_TEXT_X, top + ROW_PAD)
                c.drawLine(COL_R_X, top, COL_R_X, top + rowH, lineStroke)
                c.drawLine(COL_S_X, top, COL_S_X, top + rowH, lineStroke)
                c.drawLine(MARGIN, top + rowH, RIGHT, top + rowH, dividerStroke)
            }
            y += rowH
            shadeIndex++
        }

        fun kvRow(row: KvRow) {
            val labelX = if (row.indent) COL_Q_TEXT_X + 14f else COL_Q_TEXT_X
            val labelW = if (row.indent) COL_Q_W - 14f else COL_Q_W
            val lLay = layout(row.label, qBoldPaint, labelW)
            val vLay = layout(row.value, qPaint, VALUE_W)
            val rowH = maxOf(lLay.height, vLay.height) + ROW_PAD * 2
            ensure(rowH)
            val top = y
            val shade = shadeIndex % 2 == 1
            add { c ->
                if (shade) c.drawRect(MARGIN, top, RIGHT, top + rowH, shadeFill)
                drawLayout(c, lLay, labelX, top + ROW_PAD)
                drawLayout(c, vLay, COL_R_TEXT_X, top + ROW_PAD)
                c.drawLine(COL_R_X, top, COL_R_X, top + rowH, lineStroke)
                c.drawLine(MARGIN, top + rowH, RIGHT, top + rowH, dividerStroke)
            }
            y += rowH
            shadeIndex++
        }

        fun subHeader(text: String) {
            val lay = layout(text, subPaint, CONTENT_W)
            ensure(lay.height + 26f)
            val top = y
            add { c -> drawLayout(c, lay, MARGIN, top + 7f) }
            y += 7f + lay.height + 3f
        }

        // ---- free-text notes (may split across pages, line by line) ----
        fun beginNoteBlock() {
            if (inTable) {
                inTable = false
                noteResumesTable = true
            }
        }

        fun endNoteBlock() {
            if (!noteResumesTable) return
            noteResumesTable = false
            ensure(COL_H + 30f)
            colHeader()
            inTable = true
        }

        fun noteRow(row: NoteRow) {
            ensure(30f)
            val lTop = y
            val lLay = layout(row.label, noteLabelPaint, CONTENT_W)
            add { c -> drawLayout(c, lLay, MARGIN, lTop + 2f) }
            y += 2f + lLay.height + 2f

            var remaining = row.text.trim()
            while (remaining.isNotEmpty()) {
                val avail = CONTENT_BOTTOM - y
                val full = layout(remaining, notePaint, CONTENT_W - 4f)
                if (full.height <= avail) {
                    val t = y
                    add { c -> drawLayout(c, full, MARGIN + 2f, t) }
                    y += full.height + 10f
                    remaining = ""
                } else {
                    if (avail < 16f) {
                        newPage()
                        continue
                    }
                    var lines = 0
                    while (lines < full.lineCount && full.getLineTop(lines + 1) <= avail) lines++
                    if (lines == 0) {
                        newPage()
                        continue
                    }
                    val endIdx = full.getLineEnd(lines - 1)
                    val segment = remaining.substring(0, endIdx).trimEnd()
                    val segLay = layout(segment, notePaint, CONTENT_W - 4f)
                    val t = y
                    add { c -> drawLayout(c, segLay, MARGIN + 2f, t) }
                    remaining = remaining.substring(endIdx).trimStart()
                    if (remaining.isNotEmpty()) newPage() else y += segLay.height + 10f
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Content building
    // ------------------------------------------------------------------
    private fun summaryVisualRows(patient: PatientEntity, form: AssessmentFormEntity): List<List<CardCell>> {
        val cells = mutableListOf<CardCell>()
        if (patient.name.isNotBlank()) cells += CardCell("Patient name", patient.name)
        if (patient.mrdNo.isNotBlank()) cells += CardCell("MRD No.", patient.mrdNo)
        patient.age?.let { cells += CardCell("Age", "$it years") }
        if (patient.gender.isNotBlank()) cells += CardCell("Gender", patient.gender)
        if (patient.diagnosis.isNotBlank()) cells += CardCell("Diagnosis", patient.diagnosis, span = true)
        if (patient.procedureDate.isNotBlank()) cells += CardCell("Procedure date", fmtDate(patient.procedureDate))
        if (patient.dischargeDate.isNotBlank()) cells += CardCell("Discharge date", fmtDate(patient.dischargeDate))
        if (patient.category.isNotBlank()) cells += CardCell("Category", patient.category)
        form.psPathway?.takeIf { it.isNotBlank() }?.let { cells += CardCell("Pathway", it) }
        cells += CardCell("Status", if (patient.isCompleted) "Completed" else "In progress")

        val rows = mutableListOf<List<CardCell>>()
        var pending: CardCell? = null
        fun flush() {
            pending?.let { rows += listOf(it) }
            pending = null
        }
        for (cell in cells) {
            if (cell.span) {
                flush()
                rows += listOf(cell)
            } else {
                val p = pending
                if (p == null) pending = cell else {
                    rows += listOf(p, cell)
                    pending = null
                }
            }
        }
        flush()
        return rows
    }

    private fun buildSections(f: AssessmentFormEntity): List<Section> {
        val sections = mutableListOf<Section>()

        // ---- Primary (1°) ----
        val hasPrimary = f.pVentriclesAdequate != null || f.pVentriclesAdequateLikert != null ||
            f.pMarginsReachable != null || f.pMarginsReachableLikert != null ||
            f.pHinderTricuspid != null || f.pHinderTricuspidLikert != null ||
            f.pBaffleLengthMm != null ||
            f.pApproachAtriotomy || f.pApproachVentriculotomy || f.pApproachVessels ||
            f.pVsdSufficient != null || f.pVsdSufficientLikert != null ||
            f.pVsdEnlargeDirection.isNotBlank() ||
            f.pConduitRequired != null || f.pConduitRequiredLikert != null ||
            f.pPlan.isNotBlank()
        if (hasPrimary) {
            val rows = mutableListOf<Row>()
            rows += qa("1. Are the ventricles adequate / suitable for a biventricular repair?",
                f.pVentriclesAdequate, f.pVentriclesAdequateLikert)
            rows += SubRow("2. Routability concern")
            rows += qa("a. Are the margins for baffling within reach or accessible?",
                f.pMarginsReachable, f.pMarginsReachableLikert)
            rows += qa("b. Does baffling hinder tricuspid inflow?",
                f.pHinderTricuspid, f.pHinderTricuspidLikert)
            rows += KvRow("c. Simulated baffle length", mmText(f.pBaffleLengthMm))
            rows += KvRow("d. Surgical approach",
                approachText(f.pApproachAtriotomy, f.pApproachVentriculotomy, f.pApproachVessels))
            rows += qa("e. Is the VSD size sufficient for effective routing?",
                f.pVsdSufficient, f.pVsdSufficientLikert)
            if (f.pVsdSufficient == "NO") {
                rows += KvRow("VSD enlarge direction", dash(f.pVsdEnlargeDirection), indent = true)
            }
            rows += qa("3. Is there a requirement for a conduit?",
                f.pConduitRequired, f.pConduitRequiredLikert)
            if (f.pPlan.isNotBlank()) rows += NoteRow("Plan", f.pPlan)
            sections += Section(
                "Primary Assessment (1°)",
                "Modality: Echocardiography, CT-Scan / MRI-Scan",
                rows
            )
        }

        // ---- Secondary (2°) VR/3DP ----
        val hasSecondary = f.sRedoSternotomy.isNotBlank() ||
            f.sVentriclesAdequate != null || f.sVentriclesAdequateLikert != null ||
            f.sPatchPrediction != null || f.sPatchPredictionLikert != null ||
            f.sBaffleLengthMm != null || f.sBreadth1Mm != null ||
            f.sBreadth2Mm != null || f.sBreadth3Mm != null ||
            f.sMarginsReachable != null || f.sMarginsReachableLikert != null ||
            f.sHinderTricuspid != null || f.sHinderTricuspidLikert != null ||
            f.sApproachAtriotomy || f.sApproachVentriculotomy || f.sApproachVessels ||
            f.sVsdSufficient != null || f.sVsdSufficientLikert != null ||
            f.sVsdEnlargeDirection.isNotBlank() ||
            f.sRemoveConalSeptum != null || f.sRemoveConalSeptumLikert != null ||
            f.sConduitRequired != null || f.sConduitRequiredLikert != null ||
            f.sPlan.isNotBlank()
        if (hasSecondary) {
            val rows = mutableListOf<Row>()
            rows += KvRow("1. Redo-sternotomy assessment", dash(f.sRedoSternotomy))
            rows += qa("2. Are the ventricles adequate / suitable for a biventricular repair?",
                f.sVentriclesAdequate, f.sVentriclesAdequateLikert)
            rows += SubRow("3. Routability")
            rows += qa("a. Patch prediction", f.sPatchPrediction, f.sPatchPredictionLikert)
            if (f.sPatchPrediction == "YES") {
                rows += KvRow("Simulated baffle length", mmText(f.sBaffleLengthMm), indent = true)
                rows += KvRow("Breadth 1 (near VSD)", mmText(f.sBreadth1Mm), indent = true)
                rows += KvRow("Breadth 2 (mid-level)", mmText(f.sBreadth2Mm), indent = true)
                rows += KvRow("Breadth 3 (near aorta)", mmText(f.sBreadth3Mm), indent = true)
            }
            rows += qa("b. Are the margins for baffling within reach or accessible?",
                f.sMarginsReachable, f.sMarginsReachableLikert)
            rows += qa("c. Does baffling hinder tricuspid inflow?",
                f.sHinderTricuspid, f.sHinderTricuspidLikert)
            rows += KvRow("d. Surgical approach",
                approachText(f.sApproachAtriotomy, f.sApproachVentriculotomy, f.sApproachVessels))
            rows += qa("e. Is the VSD size sufficient for effective routing?",
                f.sVsdSufficient, f.sVsdSufficientLikert)
            if (f.sVsdSufficient == "NO") {
                rows += KvRow("VSD enlarge direction", dash(f.sVsdEnlargeDirection), indent = true)
            }
            rows += qa("f. Is it necessary to remove the conal septum?",
                f.sRemoveConalSeptum, f.sRemoveConalSeptumLikert)
            rows += qa("4. Is there a requirement for a conduit?",
                f.sConduitRequired, f.sConduitRequiredLikert)
            if (f.sPlan.isNotBlank()) rows += NoteRow("Plan", f.sPlan)
            sections += Section(
                "Secondary Assessment — VR / 3D Printing (2°)",
                "Modality: 3D reconstruction of CT-Scan / MRI-Scan",
                rows
            )
        }

        // ---- Post-Surgical ----
        val hasPost = f.psPlanSame.isNotBlank() || f.psPlanWhyDifferent.isNotBlank() ||
            f.psRedoSternotomy.isNotBlank() ||
            f.psVentriclesAdequate != null || f.psVentriclesAdequateLikert != null ||
            f.psBaffleLengthMm != null || f.psBreadth1Mm != null ||
            f.psBreadth2Mm != null || f.psBreadth3Mm != null ||
            f.psHinderTricuspid != null || f.psHinderTricuspidLikert != null ||
            f.psMarginsReachable != null || f.psMarginsReachableLikert != null ||
            f.psConduitRequired != null || f.psConduitRequiredLikert != null ||
            f.psRemoveConalSeptum != null || f.psRemoveConalSeptumLikert != null ||
            f.psVsdSufficient != null || f.psVsdSufficientLikert != null ||
            f.psVsdEnlargeDirection.isNotBlank() ||
            f.psApproachAtriotomy || f.psApproachVentriculotomy || f.psApproachVessels ||
            f.psLikertDecision != null || f.psLikertExecution != null ||
            f.psLikertChallenges != null || f.psLikertOptimizing != null ||
            f.psCpbMinutes != null || f.psAccMinutes != null ||
            !f.psPathway.isNullOrBlank()
        if (hasPost) {
            val rows = mutableListOf<Row>()
            f.psPathway?.takeIf { it.isNotBlank() }?.let { rows += KvRow("Pathway", it) }
            rows += KvRow("1. Plan same or different", dash(f.psPlanSame))
            if (f.psPlanSame == "Different" && f.psPlanWhyDifferent.isNotBlank()) {
                rows += NoteRow("Why the plan was different", f.psPlanWhyDifferent)
            }
            rows += KvRow("2. Redo-sternotomy assessment", dash(f.psRedoSternotomy))
            rows += qa("3. Are the ventricles adequate / suitable for a biventricular repair?",
                f.psVentriclesAdequate, f.psVentriclesAdequateLikert)
            rows += SubRow("4. Surgical baffle dimensions")
            rows += KvRow("Baffle length", mmText(f.psBaffleLengthMm), indent = true)
            rows += KvRow("Breadth 1 (near VSD)", mmText(f.psBreadth1Mm), indent = true)
            rows += KvRow("Breadth 2 (mid-level)", mmText(f.psBreadth2Mm), indent = true)
            rows += KvRow("Breadth 3 (near aorta)", mmText(f.psBreadth3Mm), indent = true)
            rows += qa("5. Does baffling hinder tricuspid inflow?",
                f.psHinderTricuspid, f.psHinderTricuspidLikert)
            rows += qa("6. Are the margins for baffling within reach or accessible?",
                f.psMarginsReachable, f.psMarginsReachableLikert)
            rows += qa("7. Is there a requirement for a conduit?",
                f.psConduitRequired, f.psConduitRequiredLikert)
            rows += qa("8. Is it necessary to remove the conal septum?",
                f.psRemoveConalSeptum, f.psRemoveConalSeptumLikert)
            rows += qa("9. Is the VSD size sufficient for effective routing?",
                f.psVsdSufficient, f.psVsdSufficientLikert)
            if (f.psVsdSufficient == "NO") {
                rows += KvRow("VSD enlarge direction", dash(f.psVsdEnlargeDirection), indent = true)
            }
            rows += KvRow("10. Surgical approach",
                approachText(f.psApproachAtriotomy, f.psApproachVentriculotomy, f.psApproachVessels))
            rows += SubRow("11. Significance of VR/3DP-based planning")
            rows += scoreOnly("Decision making of the procedure", f.psLikertDecision)
            rows += scoreOnly("Execution of the procedure", f.psLikertExecution)
            rows += scoreOnly("Anticipating technical challenges", f.psLikertChallenges)
            rows += scoreOnly("Optimizing the surgical procedure", f.psLikertOptimizing)
            rows += KvRow("12. CPB time", minText(f.psCpbMinutes))
            rows += KvRow("13. ACC time", minText(f.psAccMinutes))
            sections += Section("Post-Surgical Assessment", null, rows)
        }

        return sections
    }

    // ------------------------------------------------------------------
    // Value formatting
    // ------------------------------------------------------------------
    private fun qa(question: String, tri: String?, likert: Int?): QaRow =
        QaRow(question, triText(tri), likertText(likert))

    private fun scoreOnly(question: String, likert: Int?): QaRow =
        QaRow(question, null, likertText(likert))

    private fun triText(tri: String?): String = when (tri) {
        "YES" -> "Yes"
        "NO" -> "No"
        "UNCERTAIN" -> "Uncertain"
        else -> "—"
    }

    private fun likertText(v: Int?): String = when (v) {
        1 -> "1 · Strongly Disagree"
        2 -> "2 · Disagree"
        3 -> "3 · Neutral"
        4 -> "4 · Agree"
        5 -> "5 · Strongly Agree"
        else -> "—"
    }

    private fun mmText(v: Double?): String = v?.let {
        if (it % 1.0 == 0.0) "${it.toInt()} mm" else "$it mm"
    } ?: "—"

    private fun minText(v: Int?): String = v?.let { "$it min" } ?: "—"

    private fun dash(v: String): String = v.ifBlank { "—" }

    private fun approachText(atriotomy: Boolean, ventriculotomy: Boolean, vessels: Boolean): String {
        val parts = mutableListOf<String>()
        if (atriotomy) parts += "Atriotomy"
        if (ventriculotomy) parts += "Ventriculotomy"
        if (vessels) parts += "Vessels"
        return parts.ifEmpty { listOf("—") }.joinToString(", ")
    }

    private fun fmtDate(raw: String): String = try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw)
        if (parsed != null) SimpleDateFormat("dd MMM yyyy", Locale.US).format(parsed) else raw
    } catch (e: Exception) {
        raw
    }
}
