package com.ventricle.assessment.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Local-only database. Version 2 added patients.isCompleted and
 * assessment_forms.psPathway via a real migration; version 3 adds the
 * reminders table. Existing patient data is preserved
 * (no destructive fallback anywhere).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE patients ADD COLUMN isCompleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE assessment_forms ADD COLUMN psPathway TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE reminders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "title TEXT NOT NULL, " +
                "notes TEXT NOT NULL, " +
                "dateTimeMillis INTEGER NOT NULL, " +
                "patientName TEXT NOT NULL, " +
                "mrdNumber TEXT NOT NULL, " +
                "diagnosisNote TEXT NOT NULL, " +
                "isDone INTEGER NOT NULL, " +
                "createdAt INTEGER NOT NULL)"
        )
    }
}

@Database(
    entities = [PatientEntity::class, AssessmentFormEntity::class, ReminderEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun assessmentFormDao(): AssessmentFormDao
    abstract fun reminderDao(): ReminderDao
}
