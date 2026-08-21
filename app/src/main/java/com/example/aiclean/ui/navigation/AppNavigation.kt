package com.example.aiclean.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.aiclean.ui.screens.apps.AppsScreen
import com.example.aiclean.ui.screens.duplicates.DuplicatesScreen
import com.example.aiclean.ui.screens.home.HomeScreen
import com.example.aiclean.ui.screens.junk.JunkFilesScreen
import com.example.aiclean.ui.screens.scheduler.SchedulerScreen
import com.example.aiclean.ui.screens.settings.SettingsScreen
import com.example.aiclean.ui.screens.storage.StorageScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Apps : Screen("apps")
    object Duplicates : Screen("duplicates")
    object Junk : Screen("junk")
    object Storage : Screen("storage")
    object Scheduler : Screen("scheduler")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToApps = { navController.navigate(Screen.Apps.route) },
                onNavigateToDuplicates = { navController.navigate(Screen.Duplicates.route) },
                onNavigateToJunk = { navController.navigate(Screen.Junk.route) },
                onNavigateToStorage = { navController.navigate(Screen.Storage.route) },
                onNavigateToScheduler = { navController.navigate(Screen.Scheduler.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Apps.route) {
            AppsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Duplicates.route) {
            DuplicatesScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Junk.route) {
            JunkFilesScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Storage.route) {
            StorageScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Scheduler.route) {
            SchedulerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
