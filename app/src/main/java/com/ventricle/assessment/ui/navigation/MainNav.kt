package com.ventricle.assessment.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ventricle.assessment.ui.screens.PatientFormScreen
import com.ventricle.assessment.ui.screens.PatientListScreen
import com.ventricle.assessment.ui.screens.RemindersScreen
import com.ventricle.assessment.ui.screens.SettingsScreen

private data class TopTab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TopTab("home", "Patients", Icons.Filled.Home),
    TopTab("reminders", "Reminders", Icons.Filled.Notifications),
    TopTab("settings", "Settings", Icons.Filled.Settings)
)

/**
 * Root navigation: bottom bar with Home (patient list), Reminders and
 * Settings. The patient form is a sub-route of home, so the bottom bar
 * hides while filling a form.
 */
@Composable
fun MainNav(startTab: String? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val tabRoutes = TABS.map { it.route }
    val showBottomBar = backStackEntry?.destination?.route in tabRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TABS.forEach { tab ->
                        val selected =
                            backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (startTab in tabRoutes) startTab!! else "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                PatientListScreen(
                    onOpenPatient = { id -> navController.navigate("home/form/$id") }
                )
            }
            composable(
                route = "home/form/{patientId}",
                arguments = listOf(navArgument("patientId") { type = NavType.LongType })
            ) {
                PatientFormScreen(onBack = { navController.popBackStack() })
            }
            composable("reminders") { RemindersScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
