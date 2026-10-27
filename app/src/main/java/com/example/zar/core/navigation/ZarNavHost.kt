package com.example.zar.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.core.presentation.motion.ZarSprings
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
import androidx.navigation.NavBackStackEntry
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
import com.example.zar.core.presentation.components.DriveCapsule
import com.example.zar.core.presentation.components.LocalPagerGestureLock
import com.example.zar.core.presentation.components.PagerGestureLock
import com.example.zar.core.presentation.components.TabPagerBackHandler
import com.example.zar.core.presentation.components.SheetBackHost
import com.example.zar.core.presentation.components.TabPagerHost
import com.example.zar.core.presentation.components.rememberTabPagerController
import com.example.zar.core.presentation.motion.LocalZarMotion
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.snapshotFlow
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.zar.core.presentation.components.findActivity
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
import com.example.zar.features.transactions.presentation.manual.screen.AddTransactionScreen
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
    // The tabs are "on top" only when they are the one thing visible: not while a child screen or a sheet is arriving or leaving.
    val visibleEntries by navController.visibleEntries.collectAsStateWithLifecycle()
    val tabsOnTop = visibleEntries.size == 1 && visibleEntries.single().destination.hasRoute<HomeRoute>()
    val policy = LocalZarMotion.current
    // The four tabs are the pages of one pager that lives in the single tabs destination. Its state is owned here, above
    // the NavHost, so the chrome (which sits outside the pager) can follow it and a child route popping back finds
    // the same page.
    val tabs = rememberTabPagerController()
    // Reaching any other tab reveals the full navbar, by tap, swipe, Back or restoration alike.
    LaunchedEffect(tabs) {
        snapshotFlow { tabs.pagerState.currentPage }.collect { navSession.onTopLevelArrived(TopLevelDestination.entries[it]) }
    }
    val onNavigate: (TopLevelDestination) -> Unit = { destination ->
        navSession.onTopLevelArrived(destination)
        // From a child screen, return to the tabs first; on the tabs, scroll the pager.
        if (navController.currentBackStackEntry?.destination?.hasRoute<HomeRoute>() != true) navController.popBackStack(HomeRoute, inclusive = false)
        tabs.select(destination, policy)
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
    // Route motion (see RouteTransitions): hierarchy pushes/pops from the logical end, peer tabs a short directional travel,
    // sheet-style routes from below, reduced motion a crossfade of at most 100ms. Root Android Back is never intercepted here.
    val transitions = rememberZarRouteTransitions()
    val tabsRecede = remember { mutableStateOf<State<Float>?>(null) }
    // A child that fills the screen covers the tabs (they stay composed beneath it); a dialog route does not.
    val childCovers = visibleEntries.any { !it.destination.hasRoute<HomeRoute>() && it.destination.navigatorName != "dialog" }
    val tabsHidden = visibleEntries.isNotEmpty() && visibleEntries.none { it.destination.hasRoute<HomeRoute>() }
    val onHomeTop = navController.currentBackStackEntryAsState().value?.destination?.hasRoute<HomeRoute>() != false
    val tabPage: @Composable (TopLevelDestination) -> Unit = { destination ->
                    when (destination) {
                        TopLevelDestination.Home -> HomeScreen(
                            onNavigate = onNavigate,
                            onAddOption = onAddOption,
                            onOpenReview = { navController.navigate(NeedsReviewRoute) },
                            onOpenFreelance = { navController.navigate(FreelanceRoute) },
                            onOpenBills = { navController.navigate(BillsRoute) },
                            onOpenBill = { id -> navController.navigate(BillRoute(id)) },
                        )
                        TopLevelDestination.Activity -> ActivityScreen(
                            onNavigate = onNavigate,
                            onAddOption = onAddOption,
                            onOpenEntry = { id -> navController.navigate(EntryDetailRoute(id)) },
                        )
                        TopLevelDestination.Plans -> PlansScreen(
                            onNavigate = onNavigate,
                            onAddOption = onAddOption,
                            onOpenBills = { navController.navigate(BillsRoute) },
                            onOpenFreelance = { navController.navigate(FreelanceRoute) },
                            onNewBill = { navController.navigate(BillFormRoute()) },
                            onNewProject = { navController.navigate(ProjectFormRoute()) },
                        )
                        TopLevelDestination.More -> MoreScreen(
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
                }
    CompositionLocalProvider(LocalNavChromeHost provides true) {
    Box(modifier = modifier.fillMaxSize()) {
        TabsLayer(
            tabs = tabs,
            scrollLocked = !tabsOnTop,
            blocked = childCovers,
            hidden = tabsHidden,
            onHomeTop = onHomeTop,
            recede = { tabsRecede.value?.value ?: 0f },
            chrome = {
                tabs.DriveCapsule(capsuleController)
                ZarTopLevelChrome(
                    destination = tabs.selected,
                    chrome = navSession.chromeFor(tabs.selected),
                    onNavigate = onNavigate,
                    onAddOption = onAddOption,
                    controller = capsuleController,
                    driveCapsule = false,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            },
            page = tabPage,
        )
        run {
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                enterTransition = transitions.enter,
                exitTransition = transitions.exit,
                popEnterTransition = transitions.popEnter,
                popExitTransition = transitions.popExit,
            ) {
        composable<HomeRoute> {
            // The tabs are not drawn here: they live under the NavHost so they stay composed while a child covers them
            // (see TabsLayer). This destination only reports how far the NavHost has receded it.
            TabsAnchor(transition, tabsRecede)
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
                // Add opens the one Add-transaction session on its Income tab, over this same origin.
                onAddIncome = { navController.navigate(ManualEntryRoute(initialKind = "income")) },
                onOpenIncome = { id -> navController.navigate(IncomeFormRoute(incomeId = id)) },
            )
        }
        // A dialog like the Add session, so the list (or Activity row) it was opened from stays visible behind the scrim.
        dialog<IncomeFormRoute>(
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            SheetBackHost(onPop = { navController.popBackStack() }) { dismiss -> IncomeFormScreen(onDone = dismiss) }
        }
        dialog<ManualEntryRoute>(
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) { entry ->
            val route = entry.toRoute<ManualEntryRoute>()
            SheetBackHost(onPop = { navController.popBackStack() }) { dismiss ->
                AddTransactionScreen(
                    focusNote = route.focusNote,
                    initialKind = route.initialKind,
                    onDone = dismiss,
                    onAddAccount = { navController.navigate(AccountFormRoute()) },
                )
            }
        }
        dialog<QuickNoteRoute>(
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            SheetBackHost(onPop = { navController.popBackStack() }) { dismiss ->
            QuickNoteScreen(
                onDone = dismiss,
                // The note comes along as the title, so nothing typed is lost when reading is not wanted or not possible.
                onFillForm = { note ->
                    navController.navigate(ManualEntryRoute(focusNote = true, prefillTitle = note.trim().take(120).ifEmpty { null })) {
                        popUpTo<QuickNoteRoute> { inclusive = true }
                    }
                },
            )
            }
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
    }
    }
}

/**
 * The tabs and their chrome, drawn beneath the NavHost. They are composed once and stay composed while a child route covers
 * them, so Back to the tabs reveals a page that is already there instead of composing the whole pager inside the first frame
 * of the slide (measured on the Pixel 6a: 200 ms in the debug build). A child's own surface covers them; while one does, the
 * tabs take no touches and no accessibility focus, and once nothing of them shows they are not drawn.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TabsLayer(
    tabs: com.example.zar.core.presentation.components.TabPagerController,
    scrollLocked: Boolean,
    blocked: Boolean,
    hidden: Boolean,
    onHomeTop: Boolean,
    recede: () -> Float,
    chrome: @Composable BoxScope.() -> Unit,
    page: @Composable (TopLevelDestination) -> Unit,
) {
    val lock = remember { PagerGestureLock() }
    val keyboardOpen = WindowInsets.isImeVisible
    // All four tabs open on a dark hero under the status bar, so the glyphs are light whenever the tabs are the screen.
    val activity = LocalContext.current.findActivity()
    val view = LocalView.current
    DisposableEffect(activity, view, onHomeTop) {
        if (onHomeTop) activity?.let { WindowCompat.getInsetsController(it.window, view).isAppearanceLightStatusBars = false }
        onDispose { }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val spatial = LocalZarMotion.current.allowsSpatialMotion
    CompositionLocalProvider(LocalPagerGestureLock provides lock) {
        TabPagerBackHandler(tabs, enabled = !scrollLocked)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (hidden) 0f else 1f }
                .then(if (blocked) Modifier.blockInput() else Modifier),
        ) {
            // The NavHost slides the child over the tabs and, as it arrives, recedes the tabs 0.2 of the width toward the
            // logical start (and back as it leaves). The pages follow that; the chrome stays put and is covered or uncovered
            // by the child's edge.
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    translationX = if (!spatial) 0f else -recede() * ZarMotion.ROUTE_PARENT_RECEDE * size.width * if (rtl) -1f else 1f
                },
            ) {
                TabPagerHost(
                    controller = tabs,
                    userScrollEnabled = !scrollLocked && !keyboardOpen && !lock.locked,
                    page = page,
                )
            }
            chrome()
        }
    }
}

/** Lets no touch and no accessibility focus reach what is beneath a covering child. */
private fun Modifier.blockInput(): Modifier = this
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
    .clearAndSetSemantics { }

/** The tabs destination itself is empty; it reports the NavHost's recede of the tabs (0 in place, 1 fully receded). */
@Composable
private fun AnimatedContentScope.TabsAnchor(transition: Transition<EnterExitState>, holder: MutableState<State<Float>?>) {
    // The same two specs the NavHost uses for this destination: a spring as a child arrives, a seekable tween as it leaves.
    val receded = transition.animateFloat(label = "tabsRecede", transitionSpec = {
        if (targetState == EnterExitState.Visible) tween(ZarMotion.POP_MILLIS, easing = FastOutSlowInEasing)
        else spring(ZarSprings.Route.dampingRatio, ZarSprings.Route.stiffness)
    }) { if (it == EnterExitState.Visible) 0f else 1f }
    SideEffect { holder.value = receded }
    Box(Modifier.fillMaxSize())
}
