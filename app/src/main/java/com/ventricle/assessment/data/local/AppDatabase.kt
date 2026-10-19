package com.ventricle.assessment.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Local-only database. Version 2 adds patients.isCompleted and
 * assessment_forms.psPathway via a real migration — existing patient
 * data is preserved (no destructive fallback anywhere).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE patients ADD COLUMN isCompleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE assessment_forms ADD COLUMN psPathway TEXT")
    }
}

@Database(
    entities = [PatientEntity::class, AssessmentFormEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun assessmentFormDao(): AssessmentFormDao
}
