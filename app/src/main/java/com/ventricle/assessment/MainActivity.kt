package com.ventricle.assessment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ventricle.assessment.ui.screens.AssessmentListScreen
import com.ventricle.assessment.ui.theme.VentricleAssessmentTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VentricleAssessmentTheme {
                AssessmentListScreen()
            }
        }
    }
}
