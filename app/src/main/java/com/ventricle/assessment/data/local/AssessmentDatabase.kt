package com.ventricle.assessment.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AssessmentEntity::class], version = 1, exportSchema = false)
abstract class AssessmentDatabase : RoomDatabase() {
    abstract fun assessmentDao(): AssessmentDao
}
