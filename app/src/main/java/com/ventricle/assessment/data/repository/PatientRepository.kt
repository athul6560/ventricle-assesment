package com.ventricle.assessment.data.repository

import com.ventricle.assessment.data.local.AssessmentFormDao
import com.ventricle.assessment.data.local.AssessmentFormEntity
import com.ventricle.assessment.data.local.PatientDao
import com.ventricle.assessment.data.local.PatientEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientRepository @Inject constructor(
    private val patientDao: PatientDao,
    private val formDao: AssessmentFormDao
) {

    fun observePatients(): Flow<List<PatientEntity>> = patientDao.observeAll()

    fun observeForms(): Flow<List<AssessmentFormEntity>> = formDao.observeAll()

    fun observePatient(id: Long): Flow<PatientEntity?> = patientDao.observeById(id)

    suspend fun getPatient(id: Long): PatientEntity? = patientDao.getById(id)

    /** Creates a blank patient row and returns its id. */
    suspend fun createPatient(): Long {
        val now = System.currentTimeMillis()
        return patientDao.upsert(PatientEntity(createdAt = now, updatedAt = now))
    }

    suspend fun savePatient(patient: PatientEntity) {
        patientDao.upsert(patient.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePatient(id: Long) {
        patientDao.deleteById(id) // the form row is removed via FK CASCADE
    }

    fun observeForm(patientId: Long): Flow<AssessmentFormEntity?> =
        formDao.observeByPatient(patientId)

    suspend fun getForm(patientId: Long): AssessmentFormEntity? =
        formDao.getByPatient(patientId)

    /** Upserts the form, creating the row on first save. */
    suspend fun saveForm(form: AssessmentFormEntity) {
        formDao.upsert(form.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Backup import: upserts rows directly (timestamps preserved) so a
     * restore-after-data-loss keeps the original ids and ordering.
     */
    suspend fun importPatients(patients: List<PatientEntity>) {
        patients.forEach { patientDao.upsert(it) }
    }

    suspend fun importForms(forms: List<AssessmentFormEntity>) {
        forms.forEach { formDao.upsert(it) }
    }
}
