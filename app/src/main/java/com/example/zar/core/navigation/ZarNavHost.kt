package com.example.zar.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.zar.features.home.presentation.screen.HomeScreen
import com.example.zar.features.settings.presentation.screen.SettingsScreen
import com.example.zar.features.transactions.presentation.income.screen.IncomeFormScreen
import com.example.zar.features.transactions.presentation.income.screen.IncomeListScreen

@Composable
fun ZarNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenIncome = { navController.navigate(IncomeRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<IncomeRoute> {
            IncomeListScreen(
                onBack = { navController.popBackStack() },
                onAddIncome = { navController.navigate(IncomeFormRoute()) },
                onOpenIncome = { id -> navController.navigate(IncomeFormRoute(incomeId = id)) },
            )
        }
        composable<IncomeFormRoute> {
            IncomeFormScreen(onDone = { navController.popBackStack() })
        }
    }
}
