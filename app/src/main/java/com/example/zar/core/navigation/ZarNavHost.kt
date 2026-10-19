package com.example.zar.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.zar.features.accounts.presentation.screen.AccountDetailScreen
import com.example.zar.features.accounts.presentation.screen.AccountFormScreen
import com.example.zar.features.accounts.presentation.screen.AccountsScreen
import com.example.zar.features.freelance.presentation.screen.FreelanceScreen
import com.example.zar.features.freelance.presentation.screen.MilestoneScreen
import com.example.zar.features.freelance.presentation.screen.ProjectFormScreen
import com.example.zar.features.freelance.presentation.screen.ProjectScreen
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
                onOpenFreelance = { navController.navigate(FreelanceRoute) },
                onOpenAccounts = { navController.navigate(AccountsRoute) },
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
        composable<FreelanceRoute> {
            FreelanceScreen(
                onBack = { navController.popBackStack() },
                onAddProject = { navController.navigate(ProjectFormRoute()) },
                onOpenProject = { id -> navController.navigate(ProjectRoute(id)) },
            )
        }
        composable<ProjectFormRoute> {
            ProjectFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id, isNew ->
                    navController.popBackStack()
                    // A new project opens straight away so milestones can be added.
                    if (isNew) navController.navigate(ProjectRoute(id))
                },
            )
        }
        composable<ProjectRoute> {
            ProjectScreen(
                onBack = { navController.popBackStack() },
                onEditProject = { id -> navController.navigate(ProjectFormRoute(projectId = id)) },
                onAddMilestone = { id -> navController.navigate(MilestoneRoute(projectId = id)) },
                onOpenMilestone = { projectId, milestoneId -> navController.navigate(MilestoneRoute(projectId, milestoneId)) },
            )
        }
        composable<MilestoneRoute> {
            MilestoneScreen(onDone = { navController.popBackStack() })
        }
        composable<AccountsRoute> {
            AccountsScreen(
                onBack = { navController.popBackStack() },
                onAddAccount = { navController.navigate(AccountFormRoute()) },
                onOpenAccount = { id -> navController.navigate(AccountRoute(id)) },
            )
        }
        composable<AccountRoute> {
            AccountDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(AccountFormRoute(accountId = id)) },
            )
        }
        composable<AccountFormRoute> {
            AccountFormScreen(onDone = { navController.popBackStack() })
        }
    }
}
