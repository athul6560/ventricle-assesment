package com.ventricle.assessment.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.ventricle.assessment.data.local.AssessmentFormEntity
import com.ventricle.assessment.data.local.PatientEntity
import com.ventricle.assessment.data.local.ReminderEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class ParsedBackup(
    val patients: List<PatientEntity>,
    val forms: List<AssessmentFormEntity>
)

/**
 * Minimal .xlsx writer/reader built on java.util.zip — no Apache POI.
 *
 * Writer: valid OOXML ([Content_Types].xml, _rels/.rels, xl/workbook.xml,
 * xl/_rels/workbook.xml.rels, xl/worksheets/sheetN.xml) with inline strings
 * (t="inlineStr"), so no sharedStrings part is needed. XML-escaped, numbers
 * as plain numeric cells, blank cells omitted.
 *
 * Reader: unzips, parses each sheet's sheetData rows/cells, resolving both
 * t="inlineStr" and t="s" (shared strings via xl/sharedStrings.xml, for files
 * re-saved by Excel/Sheets). Columns are mapped by header-row names; unknown
 * columns are ignored. Defensive: empty sheets, missing columns and blank
 * cells never crash it.
 */
@Singleton
class ExcelBackup @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ---------------- public API ----------------

    fun exportPatients(patients: List<PatientEntity>, forms: List<AssessmentFormEntity>): Uri {
        val bytes = writeWorkbook(
            listOf(
                Sheet("Patients", PATIENT_COLS, patients.map { patientRow(it) }),
                Sheet("Assessments", FORM_COLS, forms.map { formRow(it) })
            )
        )
        return writeToCache(bytes, "VentricleBackup")
    }

    fun exportReminders(reminders: List<ReminderEntity>): Uri {
        val bytes = writeWorkbook(
            listOf(Sheet("Reminders", REMINDER_COLS, reminders.map { reminderRow(it) }))
        )
        return writeToCache(bytes, "VentricleReminders")
    }

    /** Parses a workbook picked by the user; never throws — returns empty lists on failure. */
    fun parseBackup(input: InputStream): ParsedBackup {
        return try {
            parseBackupInternal(input)
        } catch (_: Exception) {
            ParsedBackup(emptyList(), emptyList())
        }
    }

    // ---------------- writer ----------------

    private data class Sheet(val name: String, val headers: List<String>, val rows: List<List<Any?>>)

    private fun writeWorkbook(sheets: List<Sheet>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putAndWrite("[Content_Types].xml", contentTypesXml(sheets))
            z.putAndWrite("_rels/.rels", packageRelsXml())
            z.putAndWrite("xl/workbook.xml", workbookXml(sheets))
            z.putAndWrite("xl/_rels/workbook.xml.rels", workbookRelsXml(sheets))
            sheets.forEachIndexed { i, sheet ->
                z.putAndWrite("xl/worksheets/sheet${i + 1}.xml", sheetXml(sheet))
            }
        }
        return bos.toByteArray()
    }

    private fun ZipOutputStream.putAndWrite(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun contentTypesXml(sheets: List<Sheet>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        sheets.forEachIndexed { i, _ ->
            append("""<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun packageRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private fun workbookXml(sheets: List<Sheet>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("<sheets>")
        sheets.forEachIndexed { i, s ->
            append("""<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRelsXml(sheets: List<Sheet>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        sheets.forEachIndexed { i, _ ->
            append("""<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>""")
        }
        append("</Relationships>")
    }

    private fun sheetXml(sheet: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        val allRows = listOf(sheet.headers) + sheet.rows
        allRows.forEachIndexed { ri, row ->
            append("""<row r="${ri + 1}">""")
            row.forEachIndexed { ci, v ->
                if (v != null && !(v is String && v.isEmpty())) {
                    append(cellXml("${colName(ci)}${ri + 1}", v))
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun cellXml(ref: String, v: Any): String = when (v) {
        is Boolean -> """<c r="$ref"><v>${if (v) 1 else 0}</v></c>"""
        is Number -> """<c r="$ref"><v>$v</v></c>"""
        else -> """<c r="$ref" t="inlineStr"><is><t>${esc(v.toString())}</t></is></c>"""
    }

    private fun colName(i: Int): String {
        var n = i
        var s = ""
        do {
            s = ('A' + n % 26) + s
            n = n / 26 - 1
        } while (n >= 0)
        return s
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun writeToCache(bytes: ByteArray, prefix: String): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(context.cacheDir, "${prefix}_${stamp}.xlsx")
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // ---------------- column definitions ----------------

    private val PATIENT_COLS = listOf(
        "id", "name", "mrdNo", "gender", "age", "diagnosis",
        "procedureDate", "dischargeDate", "category", "isCompleted",
        "createdAt", "updatedAt"
    )

    private val FORM_COLS = listOf(
        "id", "patientId",
        "pVentriclesAdequate", "pVentriclesAdequateLikert",
        "pMarginsReachable", "pMarginsReachableLikert",
        "pHinderTricuspid", "pHinderTricuspidLikert",
        "pBaffleLengthMm",
        "pApproachAtriotomy", "pApproachVentriculotomy", "pApproachVessels",
        "pVsdSufficient", "pVsdSufficientLikert", "pVsdEnlargeDirection",
        "pConduitRequired", "pConduitRequiredLikert", "pPlan",
        "sRedoSternotomy",
        "sVentriclesAdequate", "sVentriclesAdequateLikert",
        "sPatchPrediction", "sPatchPredictionLikert",
        "sBaffleLengthMm", "sBreadth1Mm", "sBreadth2Mm", "sBreadth3Mm",
        "sMarginsReachable", "sMarginsReachableLikert",
        "sHinderTricuspid", "sHinderTricuspidLikert",
        "sApproachAtriotomy", "sApproachVentriculotomy", "sApproachVessels",
        "sVsdSufficient", "sVsdSufficientLikert", "sVsdEnlargeDirection",
        "sRemoveConalSeptum", "sRemoveConalSeptumLikert",
        "sConduitRequired", "sConduitRequiredLikert", "sPlan",
        "psPlanSame", "psPlanWhyDifferent", "psRedoSternotomy",
        "psVentriclesAdequate", "psVentriclesAdequateLikert",
        "psBaffleLengthMm", "psBreadth1Mm", "psBreadth2Mm", "psBreadth3Mm",
        "psHinderTricuspid", "psHinderTricuspidLikert",
        "psMarginsReachable", "psMarginsReachableLikert",
        "psConduitRequired", "psConduitRequiredLikert",
        "psRemoveConalSeptum", "psRemoveConalSeptumLikert",
        "psVsdSufficient", "psVsdSufficientLikert", "psVsdEnlargeDirection",
        "psApproachAtriotomy", "psApproachVentriculotomy", "psApproachVessels",
        "psLikertDecision", "psLikertExecution", "psLikertChallenges", "psLikertOptimizing",
        "psCpbMinutes", "psAccMinutes", "psPathway",
        "updatedAt"
    )

    private val REMINDER_COLS = listOf(
        "id", "title", "notes", "dateTimeMillis", "dateTime",
        "patientName", "mrdNumber", "diagnosisNote", "isDone", "createdAt"
    )

    private fun patientRow(p: PatientEntity): List<Any?> = listOf(
        p.id, p.name, p.mrdNo, p.gender, p.age, p.diagnosis,
        p.procedureDate, p.dischargeDate, p.category, p.isCompleted,
        p.createdAt, p.updatedAt
    )

    private fun formRow(f: AssessmentFormEntity): List<Any?> = listOf(
        f.id, f.patientId,
        f.pVentriclesAdequate, f.pVentriclesAdequateLikert,
        f.pMarginsReachable, f.pMarginsReachableLikert,
        f.pHinderTricuspid, f.pHinderTricuspidLikert,
        f.pBaffleLengthMm,
        f.pApproachAtriotomy, f.pApproachVentriculotomy, f.pApproachVessels,
        f.pVsdSufficient, f.pVsdSufficientLikert, f.pVsdEnlargeDirection,
        f.pConduitRequired, f.pConduitRequiredLikert, f.pPlan,
        f.sRedoSternotomy,
        f.sVentriclesAdequate, f.sVentriclesAdequateLikert,
        f.sPatchPrediction, f.sPatchPredictionLikert,
        f.sBaffleLengthMm, f.sBreadth1Mm, f.sBreadth2Mm, f.sBreadth3Mm,
        f.sMarginsReachable, f.sMarginsReachableLikert,
        f.sHinderTricuspid, f.sHinderTricuspidLikert,
        f.sApproachAtriotomy, f.sApproachVentriculotomy, f.sApproachVessels,
        f.sVsdSufficient, f.sVsdSufficientLikert, f.sVsdEnlargeDirection,
        f.sRemoveConalSeptum, f.sRemoveConalSeptumLikert,
        f.sConduitRequired, f.sConduitRequiredLikert, f.sPlan,
        f.psPlanSame, f.psPlanWhyDifferent, f.psRedoSternotomy,
        f.psVentriclesAdequate, f.psVentriclesAdequateLikert,
        f.psBaffleLengthMm, f.psBreadth1Mm, f.psBreadth2Mm, f.psBreadth3Mm,
        f.psHinderTricuspid, f.psHinderTricuspidLikert,
        f.psMarginsReachable, f.psMarginsReachableLikert,
        f.psConduitRequired, f.psConduitRequiredLikert,
        f.psRemoveConalSeptum, f.psRemoveConalSeptumLikert,
        f.psVsdSufficient, f.psVsdSufficientLikert, f.psVsdEnlargeDirection,
        f.psApproachAtriotomy, f.psApproachVentriculotomy, f.psApproachVessels,
        f.psLikertDecision, f.psLikertExecution, f.psLikertChallenges, f.psLikertOptimizing,
        f.psCpbMinutes, f.psAccMinutes, f.psPathway,
        f.updatedAt
    ).also { require(it.size == FORM_COLS.size) { "form row/header size mismatch" } }

    private fun reminderRow(r: ReminderEntity): List<Any?> {
        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            .format(Date(r.dateTimeMillis))
        return listOf(
            r.id, r.title, r.notes, r.dateTimeMillis, dateStr,
            r.patientName, r.mrdNumber, r.diagnosisNote, r.isDone, r.createdAt
        )
    }

    // ---------------- reader ----------------

    private fun parseBackupInternal(input: InputStream): ParsedBackup {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory) entries[e.name] = z.readBytes()
                z.closeEntry()
                e = z.nextEntry
            }
        }
        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        val wbBytes = entries["xl/workbook.xml"] ?: return ParsedBackup(emptyList(), emptyList())
        val targets = workbookSheetTargets(wbBytes, entries["xl/_rels/workbook.xml.rels"])
        val patients = targets["Patients"]?.let { entries[it] }
            ?.let { rowsToPatients(parseSheet(it, shared)) } ?: emptyList()
        val forms = targets["Assessments"]?.let { entries[it] }
            ?.let { rowsToForms(parseSheet(it, shared)) } ?: emptyList()
        return ParsedBackup(patients, forms)
    }

    /** Maps sheet name -> "xl/..." part path via workbook.xml + its .rels. */
    private fun workbookSheetTargets(wbBytes: ByteArray, relsBytes: ByteArray?): Map<String, String> {
        val root = parseXml(wbBytes) ?: return emptyMap()
        val relMap = mutableMapOf<String, String>()
        relsBytes?.let { parseXml(it) }?.let { relsRoot ->
            val nodes = relsRoot.getElementsByTagName("Relationship")
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as Element
                relMap[el.getAttribute("Id")] = el.getAttribute("Target")
            }
        }
        val out = mutableMapOf<String, String>()
        val sheets = root.getElementsByTagName("sheet")
        for (i in 0 until sheets.length) {
            val el = sheets.item(i) as Element
            val target = relMap[el.getAttribute("r:id")] ?: continue
            out[el.getAttribute("name")] = "xl/$target"
        }
        return out
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val root = parseXml(bytes) ?: return emptyList()
        val out = mutableListOf<String>()
        val nodes = root.getElementsByTagName("si")
        for (i in 0 until nodes.length) {
            out.add((nodes.item(i) as Element).textContent?.trim() ?: "")
        }
        return out
    }

    /** Returns data rows as header-name -> cell-text maps (header row skipped, blank rows dropped). */
    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<Map<String, String>> {
        val root = parseXml(bytes) ?: return emptyList()
        val rawRows = mutableListOf<List<Pair<Int, String>>>()
        val rowNodes = root.getElementsByTagName("row")
        for (i in 0 until rowNodes.length) {
            val rowEl = rowNodes.item(i) as Element
            val cells = mutableListOf<Pair<Int, String>>()
            val cNodes = rowEl.getElementsByTagName("c")
            for (j in 0 until cNodes.length) {
                val cEl = cNodes.item(j) as Element
                val col = colIndex(cEl.getAttribute("r"))
                if (col < 0) continue
                val value = when (cEl.getAttribute("t")) {
                    "inlineStr" -> childText(cEl, "is", "t")
                    "s" -> childText(cEl, "v").toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    else -> childText(cEl, "v")
                }
                cells.add(col to value)
            }
            if (cells.isNotEmpty()) rawRows.add(cells.sortedBy { it.first })
        }
        if (rawRows.isEmpty()) return emptyList()
        val headerByCol = rawRows.first().toMap()
        return rawRows.drop(1).mapNotNull { row ->
            val map = mutableMapOf<String, String>()
            row.forEach { (col, v) -> headerByCol[col]?.let { map[it] = v } }
            if (map.values.any { it.isNotBlank() }) map else null
        }
    }

    private fun childText(parent: Element, vararg tags: String): String {
        var current: List<Element> = listOf(parent)
        for (tag in tags) {
            val next = mutableListOf<Element>()
            for (el in current) {
                val nl = el.getElementsByTagName(tag)
                for (k in 0 until nl.length) next.add(nl.item(k) as Element)
            }
            current = next
        }
        return current.joinToString("") { it.textContent?.trim() ?: "" }
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return n - 1
    }

    private fun parseXml(bytes: ByteArray): Element? {
        return try {
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = false
            try {
                dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (_: Exception) {
            }
            dbf.newDocumentBuilder().parse(bytes.inputStream()).documentElement
        } catch (_: Exception) {
            null
        }
    }

    // ---- map -> entity (defensive conversions) ----

    private fun Map<String, String>.lng(k: String): Long =
        this[k]?.toLongOrNull() ?: this[k]?.toDoubleOrNull()?.toLong() ?: 0L

    private fun Map<String, String>.int(k: String): Int? = this[k]?.toIntOrNull()

    private fun Map<String, String>.dbl(k: String): Double? = this[k]?.toDoubleOrNull()

    private fun Map<String, String>.str(k: String): String = this[k] ?: ""

    private fun Map<String, String>.nstr(k: String): String? = this[k]?.ifBlank { null }

    private fun Map<String, String>.bool(k: String): Boolean {
        val v = this[k] ?: return false
        return v == "1" || v.equals("true", ignoreCase = true)
    }

    private fun rowsToPatients(rows: List<Map<String, String>>): List<PatientEntity> =
        rows.mapNotNull { m ->
            try {
                PatientEntity(
                    id = m.lng("id"),
                    name = m.str("name"),
                    mrdNo = m.str("mrdNo"),
                    gender = m.str("gender"),
                    age = m.int("age"),
                    diagnosis = m.str("diagnosis"),
                    procedureDate = m.str("procedureDate"),
                    dischargeDate = m.str("dischargeDate"),
                    category = m.str("category"),
                    isCompleted = m.bool("isCompleted"),
                    createdAt = m.lng("createdAt").takeIf { it != 0L } ?: System.currentTimeMillis(),
                    updatedAt = m.lng("updatedAt").takeIf { it != 0L } ?: System.currentTimeMillis()
                )
            } catch (_: Exception) {
                null
            }
        }

    private fun rowsToForms(rows: List<Map<String, String>>): List<AssessmentFormEntity> =
        rows.mapNotNull { m ->
            try {
                AssessmentFormEntity(
                    id = m.lng("id"),
                    patientId = m.lng("patientId"),
                    pVentriclesAdequate = m.nstr("pVentriclesAdequate"),
                    pVentriclesAdequateLikert = m.int("pVentriclesAdequateLikert"),
                    pMarginsReachable = m.nstr("pMarginsReachable"),
                    pMarginsReachableLikert = m.int("pMarginsReachableLikert"),
                    pHinderTricuspid = m.nstr("pHinderTricuspid"),
                    pHinderTricuspidLikert = m.int("pHinderTricuspidLikert"),
                    pBaffleLengthMm = m.dbl("pBaffleLengthMm"),
                    pApproachAtriotomy = m.bool("pApproachAtriotomy"),
                    pApproachVentriculotomy = m.bool("pApproachVentriculotomy"),
                    pApproachVessels = m.bool("pApproachVessels"),
                    pVsdSufficient = m.nstr("pVsdSufficient"),
                    pVsdSufficientLikert = m.int("pVsdSufficientLikert"),
                    pVsdEnlargeDirection = m.str("pVsdEnlargeDirection"),
                    pConduitRequired = m.nstr("pConduitRequired"),
                    pConduitRequiredLikert = m.int("pConduitRequiredLikert"),
                    pPlan = m.str("pPlan"),
                    sRedoSternotomy = m.str("sRedoSternotomy"),
                    sVentriclesAdequate = m.nstr("sVentriclesAdequate"),
                    sVentriclesAdequateLikert = m.int("sVentriclesAdequateLikert"),
                    sPatchPrediction = m.nstr("sPatchPrediction"),
                    sPatchPredictionLikert = m.int("sPatchPredictionLikert"),
                    sBaffleLengthMm = m.dbl("sBaffleLengthMm"),
                    sBreadth1Mm = m.dbl("sBreadth1Mm"),
                    sBreadth2Mm = m.dbl("sBreadth2Mm"),
                    sBreadth3Mm = m.dbl("sBreadth3Mm"),
                    sMarginsReachable = m.nstr("sMarginsReachable"),
                    sMarginsReachableLikert = m.int("sMarginsReachableLikert"),
                    sHinderTricuspid = m.nstr("sHinderTricuspid"),
                    sHinderTricuspidLikert = m.int("sHinderTricuspidLikert"),
                    sApproachAtriotomy = m.bool("sApproachAtriotomy"),
                    sApproachVentriculotomy = m.bool("sApproachVentriculotomy"),
                    sApproachVessels = m.bool("sApproachVessels"),
                    sVsdSufficient = m.nstr("sVsdSufficient"),
                    sVsdSufficientLikert = m.int("sVsdSufficientLikert"),
                    sVsdEnlargeDirection = m.str("sVsdEnlargeDirection"),
                    sRemoveConalSeptum = m.nstr("sRemoveConalSeptum"),
                    sRemoveConalSeptumLikert = m.int("sRemoveConalSeptumLikert"),
                    sConduitRequired = m.nstr("sConduitRequired"),
                    sConduitRequiredLikert = m.int("sConduitRequiredLikert"),
                    sPlan = m.str("sPlan"),
                    psPlanSame = m.str("psPlanSame"),
                    psPlanWhyDifferent = m.str("psPlanWhyDifferent"),
                    psRedoSternotomy = m.str("psRedoSternotomy"),
                    psVentriclesAdequate = m.nstr("psVentriclesAdequate"),
                    psVentriclesAdequateLikert = m.int("psVentriclesAdequateLikert"),
                    psBaffleLengthMm = m.dbl("psBaffleLengthMm"),
                    psBreadth1Mm = m.dbl("psBreadth1Mm"),
                    psBreadth2Mm = m.dbl("psBreadth2Mm"),
                    psBreadth3Mm = m.dbl("psBreadth3Mm"),
                    psHinderTricuspid = m.nstr("psHinderTricuspid"),
                    psHinderTricuspidLikert = m.int("psHinderTricuspidLikert"),
                    psMarginsReachable = m.nstr("psMarginsReachable"),
                    psMarginsReachableLikert = m.int("psMarginsReachableLikert"),
                    psConduitRequired = m.nstr("psConduitRequired"),
                    psConduitRequiredLikert = m.int("psConduitRequiredLikert"),
                    psRemoveConalSeptum = m.nstr("psRemoveConalSeptum"),
                    psRemoveConalSeptumLikert = m.int("psRemoveConalSeptumLikert"),
                    psVsdSufficient = m.nstr("psVsdSufficient"),
                    psVsdSufficientLikert = m.int("psVsdSufficientLikert"),
                    psVsdEnlargeDirection = m.str("psVsdEnlargeDirection"),
                    psApproachAtriotomy = m.bool("psApproachAtriotomy"),
                    psApproachVentriculotomy = m.bool("psApproachVentriculotomy"),
                    psApproachVessels = m.bool("psApproachVessels"),
                    psLikertDecision = m.int("psLikertDecision"),
                    psLikertExecution = m.int("psLikertExecution"),
                    psLikertChallenges = m.int("psLikertChallenges"),
                    psLikertOptimizing = m.int("psLikertOptimizing"),
                    psCpbMinutes = m.int("psCpbMinutes"),
                    psAccMinutes = m.int("psAccMinutes"),
                    psPathway = m.nstr("psPathway"),
                    updatedAt = m.lng("updatedAt").takeIf { it != 0L } ?: System.currentTimeMillis()
                )
            } catch (_: Exception) {
                null
            }
        }
}
