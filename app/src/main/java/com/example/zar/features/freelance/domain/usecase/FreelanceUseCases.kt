package com.example.zar.features.freelance.domain.usecase

import com.example.zar.core.domain.calculation.ExpectedPaymentProgressCalculator
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.ExpectedPaymentStatus
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.repository.ExpectedPaymentRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.features.freelance.domain.FreelanceCalculator
import com.example.zar.features.freelance.domain.MilestoneInput
import com.example.zar.features.freelance.domain.ProjectInput
import com.example.zar.features.freelance.domain.model.FreelanceOverview
import com.example.zar.features.freelance.domain.model.MilestoneSummary
import com.example.zar.features.freelance.domain.model.Outstanding
import com.example.zar.features.freelance.domain.model.Project
import com.example.zar.features.freelance.domain.model.ProjectId
import com.example.zar.features.freelance.domain.model.ProjectStatus
import com.example.zar.features.freelance.domain.model.ReceiptCandidate
import com.example.zar.features.freelance.domain.repository.ProjectRepository
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Why a freelance save was refused before reaching storage. */
sealed class FreelanceError(message: String) : Exception(message) {
    class ProjectArchived : FreelanceError("The project is archived")
    class MilestoneCancelled : FreelanceError("The milestone is cancelled")
    class CurrencyMismatch : FreelanceError("Amounts must use the project's currency")
    class NotIncome : FreelanceError("Only received income can be linked")
    class AmountTooHigh : FreelanceError("More than the receipt or milestone allows")
    class AmountBelowLinked : FreelanceError("Amount is below linked receipts")
}

/** Projects with milestone progress, plus what clients owe. Recomputed whenever records change. */
class ObserveFreelanceUseCase @Inject constructor(
    private val projects: ProjectRepository,
    private val expected: ExpectedPaymentRepository,
    private val ledger: LedgerRepository,
) {
    operator fun invoke(today: LocalDate): Flow<FreelanceOverview> = combine(
        projects.observeProjects(),
        expected.observeExpectedPayments(),
        expected.observeMatches(),
        ledger.observeLedger(),
    ) { projectList, payments, matches, entries ->
        FreelanceCalculator.overview(projectList, payments, matches, entries, today)
    }
}

/** User-scoped "Clients owe you" totals for Home. */
class ObserveOutstandingUseCase @Inject constructor(
    private val observeFreelance: ObserveFreelanceUseCase,
) {
    operator fun invoke(today: LocalDate): Flow<Outstanding> = observeFreelance(today).map { it.outstanding }
}

/** Receipts that can still be linked to a milestone. Empty if the milestone is unknown. */
class ObserveReceiptCandidatesUseCase @Inject constructor(
    private val expected: ExpectedPaymentRepository,
    private val ledger: LedgerRepository,
) {
    operator fun invoke(milestoneId: ExpectedPaymentId): Flow<List<ReceiptCandidate>> = combine(
        expected.observeExpectedPayments(),
        expected.observeMatches(),
        ledger.observeLedger(),
    ) { payments, matches, entries ->
        val milestone = payments.firstOrNull { it.id == milestoneId } ?: return@combine emptyList()
        FreelanceCalculator.receiptCandidates(milestone, payments, matches, entries)
    }
}

class SaveProjectUseCase @Inject constructor(
    private val projects: ProjectRepository,
) {
    /** Creates a project. Reuse the same [id] when retrying. */
    suspend fun create(id: ProjectId, input: ProjectInput, currency: Currency, now: Instant): Result<Unit> =
        attempt { projects.saveProject(Project(id, input.name, input.client, currency, ProjectStatus.ACTIVE, now)) }

    /** Renames; the currency never changes. */
    suspend fun update(project: Project, input: ProjectInput): Result<Unit> =
        attempt { projects.saveProject(project.copy(name = input.name, client = input.client)) }

    suspend fun setArchived(project: Project, archived: Boolean): Result<Unit> =
        attempt { projects.saveProject(project.copy(status = if (archived) ProjectStatus.ARCHIVED else ProjectStatus.ACTIVE)) }
}

class SaveMilestoneUseCase @Inject constructor(
    private val expected: ExpectedPaymentRepository,
    private val ledger: LedgerRepository,
) {
    /** Creates a milestone when [existing] is null, otherwise edits it. Reuse [id] on retry. */
    suspend operator fun invoke(
        project: Project,
        id: ExpectedPaymentId,
        input: MilestoneInput,
        existing: ExpectedPayment?,
        now: Instant,
    ): Result<Unit> = attempt {
        if (project.isArchived) throw FreelanceError.ProjectArchived()
        if (input.amount.currency != project.currency) throw FreelanceError.CurrencyMismatch()
        if (existing?.status == ExpectedPaymentStatus.CANCELLED) throw FreelanceError.MilestoneCancelled()
        if (existing != null && input.amount < existing.amount) {
            val (payments, matches, entries) = combine(
                expected.observeExpectedPayments(), expected.observeMatches(), ledger.observeLedger(),
            ) { payments, matches, entries ->
                Triple(payments, matches, entries)
            }.first()
            val linked = ExpectedPaymentProgressCalculator.effectiveAllocations(payments, matches, entries)
            val linkedTotal = matches.filter { it.expectedPaymentId == existing.id }
                .mapNotNull { linked[it.id] }
                .fold(Money.zero(project.currency)) { total, amount -> total + amount }
            if (input.amount < linkedTotal) throw FreelanceError.AmountBelowLinked()
        }
        val milestone = existing?.copy(title = input.title, amount = input.amount, dueDate = input.dueDate)
            ?: ExpectedPayment(
                id = id,
                direction = Direction.IN,
                amount = input.amount,
                dueDate = input.dueDate,
                title = input.title,
                source = ExpectedSource.FreelanceMilestone(project.id.value, id.value),
                createdAt = now,
            )
        expected.saveExpectedPayment(milestone)
    }
}

/** Cancelling is final; money already linked stays received. */
class CancelMilestoneUseCase @Inject constructor(
    private val expected: ExpectedPaymentRepository,
) {
    suspend operator fun invoke(milestone: ExpectedPayment): Result<Unit> = attempt {
        if (milestone.status == ExpectedPaymentStatus.CANCELLED) return@attempt
        expected.saveExpectedPayment(milestone.copy(status = ExpectedPaymentStatus.CANCELLED))
    }
}

class LinkReceiptUseCase @Inject constructor(
    private val expected: ExpectedPaymentRepository,
) {
    suspend operator fun invoke(
        milestone: MilestoneSummary,
        candidate: ReceiptCandidate,
        amount: Money,
        now: Instant,
    ): Result<Unit> = attempt {
        val target = milestone.milestone
        val receipt = candidate.entry
        if (target.status == ExpectedPaymentStatus.CANCELLED) throw FreelanceError.MilestoneCancelled()
        if (receipt.isVoided || receipt.current.type != TransactionType.INCOME) throw FreelanceError.NotIncome()
        if (amount.currency != target.amount.currency || amount.currency != receipt.current.amount.currency) {
            throw FreelanceError.CurrencyMismatch()
        }
        if (!amount.isPositive || amount > candidate.available || amount > milestone.progress.remaining) {
            throw FreelanceError.AmountTooHigh()
        }
        expected.saveMatch(PaymentMatch(target.id, receipt.id, amount, now))
    }
}

class UnlinkReceiptUseCase @Inject constructor(
    private val expected: ExpectedPaymentRepository,
) {
    suspend operator fun invoke(match: PaymentMatch): Result<Unit> =
        attempt { expected.removeMatch(match.expectedPaymentId, match.transactionId) }
}

private inline fun attempt(block: () -> Unit): Result<Unit> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
