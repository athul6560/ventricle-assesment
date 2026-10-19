package com.ventricle.assessment.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per patient. Holds the proforma header block
 * (Complex Bi-Ventricular Feasibility Assessment).
 */
@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val mrdNo: String = "",
    val gender: String = "", // "Male" / "Female" / ""
    val age: Int? = null,
    val diagnosis: String = "",
    val procedureDate: String = "", // yyyy-MM-dd
    val dischargeDate: String = "", // yyyy-MM-dd
    val category: String = "", // "Possible" / "Difficult/Doubtful" / "Not Possible" / ""
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
