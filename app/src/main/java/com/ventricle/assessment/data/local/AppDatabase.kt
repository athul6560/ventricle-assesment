package com.ventricle.assessment.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local-only database. Version 1: the app never shipped with the earlier
 * sample schema, so the entities were replaced outright (no migration).
 */
@Database(
    entities = [PatientEntity::class, AssessmentFormEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun assessmentFormDao(): AssessmentFormDao
}
