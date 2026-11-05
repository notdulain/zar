package com.example.zar.scenario

import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.model.MatchState
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.freelance.FreelanceFixtures.match
import com.example.zar.features.freelance.FreelanceFixtures.milestone
import com.example.zar.features.freelance.FreelanceFixtures.project
import com.example.zar.features.freelance.domain.FreelanceCalculator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kavindu twice forgot to chase an invoice and once could not tell which payment belonged to
 * which project (assignment §3.2). Milestones answer both.
 */
class KavinduFreelanceScenarioTest {

    private val today = LocalDate.of(2026, 10, 21)
    private val wordpress = project("wordpress", Currencies.LKR)
    private val react = project("react", Currencies.LKR, createdAtOffset = 60)

    private fun income(id: String, amount: Long, day: Int) =
        entry(tx(id, TransactionType.INCOME, lkr(amount), date = LocalDate.of(2026, 10, day)))

    @Test
    fun anUnpaidMilestonePastItsDateShowsAsOverdue() {
        val deposit = milestone("wp-deposit", "wordpress", lkr(30_000), due = LocalDate.of(2026, 10, 1))
        val final = milestone("wp-final", "wordpress", lkr(60_000), due = LocalDate.of(2026, 10, 14))
        val overview = FreelanceCalculator.overview(
            listOf(wordpress), listOf(deposit, final),
            listOf(match("wp-deposit", "r1", lkr(30_000))), listOf(income("r1", 30_000, 2)), today,
        )
        val summary = overview.projects.single()

        assertEquals(lkr(30_000), summary.received)
        assertEquals("The final LKR 60,000 is still owed", lkr(60_000), summary.remaining)
        assertEquals("…and it is past its date, so he is reminded to chase it", lkr(60_000), summary.overdue)
        assertEquals(listOf(lkr(60_000)), overview.outstanding.overdue)
    }

    @Test
    fun eachPaymentIsLinkedToExactlyOneProject() {
        val wp = milestone("wp", "wordpress", lkr(45_000))
        val rx = milestone("rx", "react", lkr(45_000))
        // Both clients pay LKR 45,000. Linking the receipt to WordPress must not mark React as paid.
        val overview = FreelanceCalculator.overview(
            listOf(wordpress, react), listOf(wp, rx),
            listOf(match("wp", "r1", lkr(45_000))), listOf(income("r1", 45_000, 10)), today,
        )
        val byProject = overview.projects.associateBy { it.project.id.value }

        assertEquals(MatchState.MATCHED, byProject.getValue("wordpress").milestones.single().progress.state)
        assertEquals(lkr(45_000), byProject.getValue("react").remaining)
        assertEquals("Clients owe him LKR 45,000 in total", listOf(lkr(45_000)), overview.outstanding.owed)
    }

    @Test
    fun aReceiptAlreadyUsedInFullIsNotOfferedAgain() {
        val wp = milestone("wp", "wordpress", lkr(45_000))
        val rx = milestone("rx", "react", lkr(45_000))
        val matches = listOf(match("wp", "r1", lkr(45_000)))
        val entries = listOf(income("r1", 45_000, 10), income("r2", 20_000, 12))

        val candidates = FreelanceCalculator.receiptCandidates(rx, listOf(wp, rx), matches, entries)

        assertEquals("Only the unused LKR 20,000 can be linked to React", listOf("r2"), candidates.map { it.entry.id.value })
        assertTrue(candidates.single().available == lkr(20_000))
    }
}
