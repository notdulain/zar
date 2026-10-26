package com.example.zar.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.zar.core.presentation.components.AddEntryOption
import com.example.zar.core.presentation.components.LocalNavCapsuleController
import com.example.zar.core.presentation.components.LocalNavChromeHost
import com.example.zar.core.presentation.components.LocalNavSession
import com.example.zar.core.presentation.components.NavCapsuleController
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.components.ZarTopLevelChrome
import com.example.zar.features.accounts.presentation.screen.AccountDetailScreen
import com.example.zar.features.accounts.presentation.screen.AccountFormScreen
import com.example.zar.features.accounts.presentation.screen.AccountsScreen
import com.example.zar.features.catchup.presentation.screen.CatchUpScreen
import com.example.zar.features.catchup.presentation.screen.NeedsReviewScreen
import com.example.zar.features.catchup.presentation.screen.ReminderSettingsScreen
import com.example.zar.features.capture.presentation.screen.CaptureReviewScreen
import com.example.zar.features.capture.presentation.screen.CaptureScreen
import com.example.zar.features.capture.presentation.viewmodel.ShareLaunchViewModel
import com.example.zar.features.bills.presentation.screen.BillFormScreen
import com.example.zar.features.bills.presentation.screen.BillDetailScreen
import com.example.zar.features.bills.presentation.screen.BillPaymentScreen
import com.example.zar.features.bills.presentation.screen.BillsScreen
import com.example.zar.features.crypto.presentation.screen.CryptoMovementScreen
import com.example.zar.features.crypto.presentation.screen.CryptoScreen
import com.example.zar.features.export.presentation.screen.ExportScreen
import com.example.zar.features.freelance.presentation.screen.FreelanceScreen
import com.example.zar.features.freelance.presentation.screen.MilestoneScreen
import com.example.zar.features.freelance.presentation.screen.ProjectFormScreen
import com.example.zar.features.freelance.presentation.screen.ProjectScreen
import com.example.zar.features.home.presentation.screen.HomeScreen
import com.example.zar.features.plans.presentation.screen.PlansScreen
import com.example.zar.features.quicknote.presentation.screen.QuickNoteScreen
import com.example.zar.features.rules.presentation.screen.CategoryRulesScreen
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
    // Text shared to Zar from another app opens the capture screen, which reads it.
    val shareLaunch: ShareLaunchViewModel = hiltViewModel()
    val hasSharedText by shareLaunch.hasSharedText.collectAsStateWithLifecycle()
    LaunchedEffect(hasSharedText) {
        if (hasSharedText) navController.navigate(CaptureRoute) { launchSingleTop = true }
    }
    // Whether the full navbar has been revealed belongs to the graph (see ZarRoot), so no screen can reset it by being
    // created again. Reaching any other tab reveals it, by tap, Back or restoration alike.
    val navSession = LocalNavSession.current
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    SideEffect { currentDestination?.topLevelDestination()?.let(navSession::onTopLevelArrived) }
    val onNavigate: (TopLevelDestination) -> Unit = {
        navSession.onTopLevelArrived(it)
        navController.navigateToTopLevel(it)
    }
    val onAddOption: (AddEntryOption) -> Unit = { option ->
        when (option) {
            AddEntryOption.Manual -> navController.navigate(ManualEntryRoute())
            AddEntryOption.QuickNote -> navController.navigate(QuickNoteRoute)
            // Voice is outside the launch scope and is no longer offered in the Add sheet.
            AddEntryOption.Voice -> Unit
        }
    }
    // The one selected-capsule mover for the session, retained across every top-level route composition.
    val capsuleController = LocalNavCapsuleController.current ?: remember { NavCapsuleController() }
    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNavChromeHost provides true) {
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
            ) {
        composable<HomeRoute> {
            HomeScreen(
                onNavigate = onNavigate,
                onAddOption = onAddOption,
                onOpenReview = { navController.navigate(NeedsReviewRoute) },
                onOpenFreelance = { navController.navigate(FreelanceRoute) },
                onOpenBills = { navController.navigate(BillsRoute) },
                onOpenBill = { id -> navController.navigate(BillRoute(id)) },
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
                onOpenCrypto = { navController.navigate(CryptoRoute) },
                onOpenCapture = { navController.navigate(CaptureRoute) },
                onOpenBills = { navController.navigate(BillsRoute) },
                onOpenReminder = { navController.navigate(ReminderSettingsRoute) },
                onOpenExport = { navController.navigate(ExportRoute) },
                onOpenRules = { navController.navigate(CategoryRulesRoute) },
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
        composable<CategoryRulesRoute> {
            CategoryRulesScreen(onBack = { navController.popBackStack() })
        }
        composable<ReminderSettingsRoute> {
            ReminderSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<ExportRoute> {
            ExportScreen(onBack = { navController.popBackStack() })
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
        dialog<QuickNoteRoute>(
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            QuickNoteScreen(
                onDone = { navController.popBackStack() },
                // The note comes along as the title, so nothing typed is lost when reading is not wanted or not possible.
                onFillForm = { note ->
                    navController.navigate(ManualEntryRoute(focusNote = true, prefillTitle = note.trim().take(120).ifEmpty { null })) {
                        popUpTo<QuickNoteRoute> { inclusive = true }
                    }
                },
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
                onOpenRules = { navController.navigate(CategoryRulesRoute) },
            )
        }
        composable<EditEntryRoute> {
            EditEntryScreen(onDone = { navController.popBackStack() })
        }
        composable<RefundRoute> {
            RefundFormScreen(onDone = { navController.popBackStack() })
        }
        composable<CaptureRoute> {
            CaptureScreen(
                onBack = { navController.popBackStack() },
                onReview = { id -> navController.navigate(CaptureReviewRoute(id)) },
                onAddManually = { navController.navigate(ManualEntryRoute()) },
            )
        }
        composable<CaptureReviewRoute> {
            CaptureReviewScreen(onDone = { navController.popBackStack() })
        }
        composable<BillsRoute> {
            BillsScreen(
                onBack = { navController.popBackStack() },
                onAddBill = { navController.navigate(BillFormRoute()) },
                onOpenBill = { id -> navController.navigate(BillRoute(id)) },
                onOpenDueDate = { id, date -> navController.navigate(BillOccurrenceRoute(id, date)) },
            )
        }
        composable<BillFormRoute> {
            BillFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id, isNew ->
                    navController.popBackStack()
                    if (isNew) navController.navigate(BillRoute(id))
                },
            )
        }
        composable<BillRoute> {
            BillDetailScreen(
                onBack = { navController.popBackStack() },
                onEditBill = { id -> navController.navigate(BillFormRoute(billId = id)) },
                onOpenDueDate = { id, date -> navController.navigate(BillOccurrenceRoute(id, date)) },
            )
        }
        composable<BillOccurrenceRoute> {
            BillPaymentScreen(onBack = { navController.popBackStack() })
        }
        composable<CryptoRoute> {
            CryptoScreen(
                onBack = { navController.popBackStack() },
                onAddMovement = { navController.navigate(CryptoMovementRoute) },
            )
        }
        composable<CryptoMovementRoute> {
            CryptoMovementScreen(onDone = { navController.popBackStack() })
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
        // One shared bottom chrome above the content NavHost: it does not move while peer-tab content travels, and its
        // selected capsule is retained here for the whole session.
        currentDestination?.topLevelDestination()?.let { topLevel ->
            ZarTopLevelChrome(
                destination = topLevel,
                chrome = navSession.chromeFor(topLevel),
                onNavigate = onNavigate,
                onAddOption = onAddOption,
                controller = capsuleController,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** The top-level tab this destination is, or null for child screens, dialogs and forms. */
private fun NavDestination.topLevelDestination(): TopLevelDestination? = when {
    hasRoute<HomeRoute>() -> TopLevelDestination.Home
    hasRoute<ActivityRoute>() -> TopLevelDestination.Activity
    hasRoute<PlansRoute>() -> TopLevelDestination.Plans
    hasRoute<MoreRoute>() -> TopLevelDestination.More
    else -> null
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
