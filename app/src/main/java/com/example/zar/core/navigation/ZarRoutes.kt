package com.example.zar.core.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation destinations. Add one entry per top-level screen.

// Signed-out
@Serializable
data object SignInRoute

@Serializable
data object RegisterRoute

// Signed-in
@Serializable
data object HomeRoute

@Serializable
data object ActivityRoute

@Serializable
data object PlansRoute

@Serializable
data object MoreRoute

@Serializable
data object SettingsRoute

@Serializable
data object IncomeRoute

/** Add income when [incomeId] is null; otherwise edit that income. */
@Serializable
data class IncomeFormRoute(val incomeId: String? = null)

@Serializable
data object FreelanceRoute

/** New project when [projectId] is null; otherwise edit it. */
@Serializable
data class ProjectFormRoute(val projectId: String? = null)

@Serializable
data class ProjectRoute(val projectId: String)

/** New milestone when [milestoneId] is null; otherwise edit it and link receipts. */
@Serializable
data class MilestoneRoute(val projectId: String, val milestoneId: String? = null)

@Serializable
data object AccountsRoute

@Serializable
data class AccountRoute(val accountId: String)

/** Add an account when [accountId] is null; otherwise edit that account. */
@Serializable
data class AccountFormRoute(val accountId: String? = null)
