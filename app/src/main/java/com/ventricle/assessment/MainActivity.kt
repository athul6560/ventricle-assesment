package com.ventricle.assessment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ventricle.assessment.ui.navigation.MainNav
import com.ventricle.assessment.ui.theme.VentricleAssessmentTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Tapping a reminder notification opens the app on the Reminders tab.
        val startTab = intent.getStringExtra("open_tab")
        setContent {
            VentricleAssessmentTheme {
                MainNav(startTab = startTab)
            }
        }
    }
}
