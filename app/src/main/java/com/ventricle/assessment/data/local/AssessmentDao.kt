package com.ventricle.assessment.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AssessmentDao {

    @Query("SELECT * FROM assessments ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AssessmentEntity>>

    @Insert
    suspend fun insert(entity: AssessmentEntity)

    @Delete
    suspend fun delete(entity: AssessmentEntity)
}
