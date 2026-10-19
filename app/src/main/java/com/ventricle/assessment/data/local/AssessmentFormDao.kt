package com.ventricle.assessment.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AssessmentFormDao {

    @Query("SELECT * FROM assessment_forms WHERE patientId = :patientId LIMIT 1")
    fun observeByPatient(patientId: Long): Flow<AssessmentFormEntity?>

    @Query("SELECT * FROM assessment_forms WHERE patientId = :patientId LIMIT 1")
    suspend fun getByPatient(patientId: Long): AssessmentFormEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(form: AssessmentFormEntity): Long

    @Query("SELECT * FROM assessment_forms")
    fun observeAll(): Flow<List<AssessmentFormEntity>>
}
