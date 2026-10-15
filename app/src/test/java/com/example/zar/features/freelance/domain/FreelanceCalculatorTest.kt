package com.example.zar.features.freelance.domain

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.correction
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.TestLedger.void
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.ExpectedPaymentStatus
import com.example.zar.core.domain.model.MatchState
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.freelance.FreelanceFixtures.TODAY
import com.example.zar.features.freelance.FreelanceFixtures.match
import com.example.zar.features.freelance.FreelanceFixtures.milestone
import com.example.zar.features.freelance.FreelanceFixtures.project
import com.example.zar.features.freelance.domain.model.ProjectStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreelanceCalculatorTest {

    private val p1 = project("p1")
    private fun income(id: String, amount: Long) = tx(id, TransactionType.INCOME, usd(amount), account = payoneer)

    @Test
    fun partialReceiptsShowReceivedRemainingAndOverdueSeparately() {
        val m1 = milestone("m1", "p1", usd(1_000), due = TODAY.minusDays(1))
        val m2 = milestone("m2", "p1", usd(500))
        val overview = FreelanceCalculator.overview(
            listOf(p1),
            listOf(m1, m2),
            listOf(match("m1", "r1", usd(400)), match("m1", "r2", usd(300))),
            listOf(entry(income("r1", 400)), entry(income("r2", 300))),
            TODAY,
        )
        val summary = overview.projects.single()
        val first = summary.milestones.first()

        assertEquals(MatchState.PARTIALLY_MATCHED, first.progress.state)
        assertEquals(usd(700), first.progress.matched)
        assertEquals(usd(300), first.progress.overdue)
        assertEquals(2, first.receipts.size)
        assertEquals(usd(1_500), summary.expected)
        assertEquals(usd(700), summary.received)
        assertEquals(usd(800), summary.remaining)
        assertEquals(usd(300), summary.overdue)
    }

    @Test
    fun onePaymentIsNeverCountedTwiceAcrossMilestones() {
        val overview = FreelanceCalculator.overview(
            listOf(p1),
            listOf(milestone("m1", "p1", usd(400)), milestone("m2", "p1", usd(400))),
            // Bad data: 800 linked from a 500 receipt. The oldest link wins; the rest is capped.
            listOf(match("m1", "r1", usd(400), offset = 1), match("m2", "r1", usd(400), offset = 2)),
            listOf(entry(income("r1", 500))),
            TODAY,
        )
        val (m1, m2) = overview.projects.single().milestones
        assertEquals(usd(400), m1.progress.matched)
        assertEquals(usd(100), m2.progress.matched)
        assertEquals(usd(500), overview.projects.single().received)
    }

    @Test
    fun correctingOrUndoingAReceiptUpdatesWhatIsOwed() {
        val receipt = income("r1", 600)
        val milestones = listOf(milestone("m1", "p1", usd(1_000)))
        val matches = listOf(match("m1", "r1", usd(600)))

        val corrected = LedgerProjector.project(listOf(receipt), listOf(correction("c1", "r1", TransactionChanges(amount = usd(250)))), emptyList())
        val afterCorrection = FreelanceCalculator.overview(listOf(p1), milestones, matches, corrected, TODAY)
        assertEquals(usd(750), afterCorrection.outstanding.owed.single())

        val undone = LedgerProjector.project(listOf(receipt), emptyList(), listOf(void("r1")))
        val afterUndo = FreelanceCalculator.overview(listOf(p1), milestones, matches, undone, TODAY)
        assertEquals(usd(1_000), afterUndo.outstanding.owed.single())
        assertTrue(afterUndo.projects.single().milestones.single().receipts.isEmpty())
    }

    @Test
    fun cancelledMilestonesAreListedButNotCounted() {
        val overview = FreelanceCalculator.overview(
            listOf(p1),
            listOf(milestone("m1", "p1", usd(1_000)), milestone("m2", "p1", usd(300), status = ExpectedPaymentStatus.CANCELLED)),
            emptyList(),
            emptyList(),
            TODAY,
        )
        val summary = overview.projects.single()
        assertEquals(2, summary.milestones.size)
        assertEquals(usd(1_000), summary.expected)
        assertEquals(usd(1_000), summary.remaining)
    }

    @Test
    fun outstandingIsPerCurrencyAndSkipsArchivedProjects() {
        val lkrProject = project("p2", Currencies.LKR, createdAtOffset = 10)
        val archived = project("p3", status = ProjectStatus.ARCHIVED)
        val overview = FreelanceCalculator.overview(
            listOf(p1, lkrProject, archived),
            listOf(
                milestone("m1", "p1", usd(400), due = TODAY.minusDays(3)),
                milestone("m2", "p2", lkr(50_000)),
                milestone("m3", "p3", usd(999)),
                // Wrong currency for its project: ignored rather than mixed in.
                milestone("m4", "p1", lkr(1)),
            ),
            emptyList(),
            emptyList(),
            TODAY,
        )
        assertEquals(listOf(lkr(50_000), usd(400)), overview.outstanding.owed)
        assertEquals(listOf(usd(400)), overview.outstanding.overdue)
        assertEquals(2, overview.outstanding.projectsWithBalance)
        assertEquals(listOf("p2", "p1", "p3"), overview.projects.map { it.project.id.value })
    }

    @Test
    fun receiptCandidatesOfferOnlyUnlinkedIncomeInTheSameCurrency() {
        val m1 = milestone("m1", "p1", usd(1_000))
        val m2 = milestone("m2", "p1", usd(1_000))
        val candidates = FreelanceCalculator.receiptCandidates(
            m2,
            listOf(m1, m2),
            listOf(match("m1", "partly", usd(300)), match("m1", "fully", usd(200)), match("m2", "linked", usd(50))),
            listOf(
                entry(income("partly", 500)),
                entry(income("fully", 200)),
                entry(income("linked", 100)),
                entry(income("undone", 100), voided = true),
                entry(tx("lkr", TransactionType.INCOME, lkr(100))),
                entry(tx("expense", TransactionType.EXPENSE, usd(100), account = payoneer)),
                entry(income("fresh", 80)),
            ),
        )
        assertEquals(listOf("partly" to usd(200), "fresh" to usd(80)), candidates.map { it.entry.id.value to it.available })
    }

    @Test
    fun milestonesAreOrderedByDueDate() {
        val overview = FreelanceCalculator.overview(
            listOf(p1),
            listOf(milestone("late", "p1", usd(1), due = TODAY.plusDays(30)), milestone("soon", "p1", usd(1), due = TODAY)),
            emptyList(),
            emptyList(),
            TODAY,
        )
        assertEquals(listOf("soon", "late"), overview.projects.single().milestones.map { it.milestone.id.value })
        assertEquals(T0, overview.projects.single().project.createdAt)
    }
}
