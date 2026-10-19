package com.ventricle.assessment.data.repository

import com.ventricle.assessment.data.local.AssessmentDao
import com.ventricle.assessment.data.local.AssessmentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssessmentRepository @Inject constructor(
    private val dao: AssessmentDao
) {
    fun observeAssessments(): Flow<List<AssessmentEntity>> = dao.observeAll()

    suspend fun addAssessment(title: String, score: Int) {
        dao.insert(
            AssessmentEntity(
                title = title,
                score = score,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun remove(entity: AssessmentEntity) {
        dao.delete(entity)
    }
}
