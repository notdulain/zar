package com.example.zar.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.zar.core.presentation.components.AddEntryOption
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.features.home.presentation.screen.HomeScreen
import com.example.zar.features.plans.presentation.screen.PlansScreen
import com.example.zar.features.settings.presentation.screen.MoreScreen
import com.example.zar.features.settings.presentation.screen.SettingsScreen
import com.example.zar.features.transactions.presentation.screen.ActivityScreen

@Composable
fun ZarNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val onNavigate: (TopLevelDestination) -> Unit = { navController.navigateToTopLevel(it) }
    // Voice, quick note and manual entry screens are added by their feature owners; until then
    // the Add sheet simply closes.
    val onAddOption: (AddEntryOption) -> Unit = {}

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            HomeScreen(onNavigate = onNavigate, onAddOption = onAddOption)
        }
        composable<ActivityRoute> {
            ActivityScreen(onNavigate = onNavigate, onAddOption = onAddOption)
        }
        composable<PlansRoute> {
            PlansScreen(onNavigate = onNavigate, onAddOption = onAddOption)
        }
        composable<MoreRoute> {
            MoreScreen(
                onNavigate = onNavigate,
                onAddOption = onAddOption,
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** Switches between main areas without stacking copies; Back from any of them returns to Home. */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    val route: Any = when (destination) {
        TopLevelDestination.Home -> HomeRoute
        TopLevelDestination.Activity -> ActivityRoute
        TopLevelDestination.Plans -> PlansRoute
        TopLevelDestination.More -> MoreRoute
    }
    navigate(route) {
        popUpTo<HomeRoute>()
        launchSingleTop = true
    }
}
