package com.example.zar.features.freelance.domain.model

import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentProgress
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.Money
import java.time.Instant

@JvmInline value class ProjectId(val value: String)

enum class ProjectStatus { ACTIVE, ARCHIVED }

/**
 * A client project. Its milestones are expected payments (source FreelanceMilestone) in the
 * project's [currency], which never changes, so totals never mix currencies.
 */
data class Project(
    val id: ProjectId,
    val name: String,
    val client: String?,
    val currency: Currency,
    val status: ProjectStatus,
    val createdAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Project name is required" }
    }

    val isArchived: Boolean get() = status == ProjectStatus.ARCHIVED
}

/** An income receipt linked to a milestone and how much of it counts there. */
data class LinkedReceipt(
    val match: PaymentMatch,
    val entry: LedgerEntry,
    /** The part of the link that counts after capping, so one payment is never counted twice. */
    val counted: Money,
)

data class MilestoneSummary(
    val progress: ExpectedPaymentProgress,
    val receipts: List<LinkedReceipt>,
) {
    val milestone: ExpectedPayment get() = progress.expected
}

/** Totals in the project's currency. Cancelled milestones are listed but not counted. */
data class ProjectSummary(
    val project: Project,
    val milestones: List<MilestoneSummary>,
    val expected: Money,
    val received: Money,
    val remaining: Money,
    val overdue: Money,
)

/** What clients still owe across active projects, one total per currency. Never converted. */
data class Outstanding(
    val owed: List<Money>,
    val overdue: List<Money>,
    val projectsWithBalance: Int,
) {
    val isEmpty: Boolean get() = owed.isEmpty()
}

data class FreelanceOverview(
    /** Active projects first, then archived; newest first within each. */
    val projects: List<ProjectSummary>,
    val outstanding: Outstanding,
)

/** Income that still has an unlinked amount available for a milestone. */
data class ReceiptCandidate(val entry: LedgerEntry, val available: Money)
