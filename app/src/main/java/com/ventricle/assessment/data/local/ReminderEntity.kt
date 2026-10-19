package com.ventricle.assessment.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A follow-up reminder. Fires an exact-alarm notification at [dateTimeMillis].
 * patientName / mrdNumber / diagnosisNote are free text captured on the
 * reminder form (shown in the list, calendar detail and the notification).
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val notes: String = "",
    val dateTimeMillis: Long = System.currentTimeMillis(),
    val patientName: String = "",
    val mrdNumber: String = "",
    val diagnosisNote: String = "",
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
