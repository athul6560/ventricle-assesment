package com.ventricle.assessment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ventricle.assessment.ui.screens.PatientFormScreen
import com.ventricle.assessment.ui.screens.PatientListScreen
import com.ventricle.assessment.ui.theme.VentricleAssessmentTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VentricleAssessmentTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "list") {
                    composable("list") {
                        PatientListScreen(
                            onOpenPatient = { id -> navController.navigate("form/$id") }
                        )
                    }
                    composable(
                        route = "form/{patientId}",
                        arguments = listOf(navArgument("patientId") { type = NavType.LongType })
                    ) {
                        PatientFormScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
