package com.ventricle.assessment.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventricle.assessment.data.local.AssessmentEntity
import com.ventricle.assessment.data.repository.AssessmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssessmentViewModel @Inject constructor(
    private val repository: AssessmentRepository
) : ViewModel() {

    val assessments: StateFlow<List<AssessmentEntity>> =
        repository.observeAssessments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addAssessment(title: String, score: Int) {
        viewModelScope.launch {
            repository.addAssessment(title, score)
        }
    }

    fun remove(entity: AssessmentEntity) {
        viewModelScope.launch {
            repository.remove(entity)
        }
    }
}
