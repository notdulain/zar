package com.example.zar.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.zar.core.presentation.components.AddEntryOption
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.features.accounts.presentation.screen.AccountDetailScreen
import com.example.zar.features.accounts.presentation.screen.AccountFormScreen
import com.example.zar.features.accounts.presentation.screen.AccountsScreen
import com.example.zar.features.catchup.presentation.screen.CatchUpScreen
import com.example.zar.features.catchup.presentation.screen.NeedsReviewScreen
import com.example.zar.features.catchup.presentation.screen.ReminderSettingsScreen
import com.example.zar.features.freelance.presentation.screen.FreelanceScreen
import com.example.zar.features.freelance.presentation.screen.MilestoneScreen
import com.example.zar.features.freelance.presentation.screen.ProjectFormScreen
import com.example.zar.features.freelance.presentation.screen.ProjectScreen
import com.example.zar.features.home.presentation.screen.HomeScreen
import com.example.zar.features.plans.presentation.screen.PlansScreen
import com.example.zar.features.settings.presentation.screen.MoreScreen
import com.example.zar.features.settings.presentation.screen.SettingsScreen
import com.example.zar.features.transactions.presentation.activity.screen.EditEntryScreen
import com.example.zar.features.transactions.presentation.activity.screen.EntryDetailScreen
import com.example.zar.features.transactions.presentation.income.screen.IncomeFormScreen
import com.example.zar.features.transactions.presentation.income.screen.IncomeListScreen
import com.example.zar.features.transactions.presentation.manual.screen.ManualEntryScreen
import com.example.zar.features.transactions.presentation.refund.screen.RefundFormScreen
import com.example.zar.features.transactions.presentation.screen.ActivityScreen

@Composable
fun ZarNavHost(
    modifier: Modifier = Modifier,
    openCatchUp: Boolean = false,
    onCatchUpOpened: () -> Unit = {},
) {
    val navController = rememberNavController()
    // The weekly reminder opens the catch-up on top of Home.
    LaunchedEffect(openCatchUp) {
        if (openCatchUp) {
            navController.navigate(CatchUpRoute) { launchSingleTop = true }
            onCatchUpOpened()
        }
    }
    val onNavigate: (TopLevelDestination) -> Unit = { navController.navigateToTopLevel(it) }
    val onAddOption: (AddEntryOption) -> Unit = { option ->
        when (option) {
            AddEntryOption.Manual -> navController.navigate(ManualEntryRoute())
            AddEntryOption.QuickNote -> navController.navigate(ManualEntryRoute(focusNote = true))
            // Voice is outside the launch scope and is no longer offered in the Add sheet.
            AddEntryOption.Voice -> Unit
        }
    }
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onNavigate = onNavigate,
                onAddOption = onAddOption,
                onOpenReview = { navController.navigate(NeedsReviewRoute) },
            )
        }
        composable<ActivityRoute> {
            ActivityScreen(
                onNavigate = onNavigate,
                onAddOption = onAddOption,
                onOpenEntry = { id -> navController.navigate(EntryDetailRoute(id)) },
            )
        }
        composable<PlansRoute> {
            PlansScreen(onNavigate = onNavigate, onAddOption = onAddOption)
        }
        composable<MoreRoute> {
            MoreScreen(
                onNavigate = onNavigate,
                onAddOption = onAddOption,
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenAccounts = { navController.navigate(AccountsRoute) },
                onOpenIncome = { navController.navigate(IncomeRoute) },
                onOpenFreelance = { navController.navigate(FreelanceRoute) },
                onOpenReminder = { navController.navigate(ReminderSettingsRoute) },
            )
        }
        composable<NeedsReviewRoute> {
            NeedsReviewScreen(
                onBack = { navController.popBackStack() },
                onOpenEntry = { id -> navController.navigate(EntryDetailRoute(id)) },
                onStartCatchUp = { navController.navigate(CatchUpRoute) },
            )
        }
        composable<CatchUpRoute> {
            CatchUpScreen(
                onClose = { navController.popBackStack() },
                onGoHome = { navController.popBackStack(HomeRoute, inclusive = false) },
                onOpenEntry = { id -> navController.navigate(EntryDetailRoute(id)) },
                onAddCashAccount = { navController.navigate(AccountFormRoute()) },
            )
        }
        composable<ReminderSettingsRoute> {
            ReminderSettingsScreen(onBack = { navController.popBackStack() })
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
        dialog<ManualEntryRoute>(
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) { entry ->
            ManualEntryScreen(
                focusNote = entry.toRoute<ManualEntryRoute>().focusNote,
                onDone = { navController.popBackStack() },
                // The Income tab of the Add form is the existing income screen; swap it in place.
                onOpenIncome = {
                    navController.navigate(IncomeFormRoute()) { popUpTo<ManualEntryRoute> { inclusive = true } }
                },
                onAddAccount = { navController.navigate(AccountFormRoute()) },
            )
        }
        composable<EntryDetailRoute> {
            EntryDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(EditEntryRoute(id)) },
                onEditIncome = { id -> navController.navigate(IncomeFormRoute(incomeId = id)) },
                onAddAgain = { navController.navigate(ManualEntryRoute()) },
                onRefund = { id -> navController.navigate(RefundRoute(id)) },
                onOpenEntry = { id -> navController.navigate(EntryDetailRoute(id)) },
            )
        }
        composable<EditEntryRoute> {
            EditEntryScreen(onDone = { navController.popBackStack() })
        }
        composable<RefundRoute> {
            RefundFormScreen(onDone = { navController.popBackStack() })
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
                onAddMissingEntry = { id, amount ->
                    navController.navigate(ManualEntryRoute(prefillAmount = amount, prefillAccountId = id))
                },
            )
        }
        composable<AccountFormRoute> {
            AccountFormScreen(onDone = { navController.popBackStack() })
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
