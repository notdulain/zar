package com.example.zar.features.reminders

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.model.ExpectedPaymentStatus
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.bills.data.repository.BillDocumentMapper
import com.example.zar.features.bills.domain.BillSchedule
import com.example.zar.features.bills.domain.BillsCalculator
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.BillReminder
import com.example.zar.features.bills.domain.model.BillStatus
import com.example.zar.features.bills.domain.model.BillsOverview
import com.example.zar.features.freelance.FreelanceFixtures.milestone
import com.example.zar.features.freelance.FreelanceFixtures.project
import com.example.zar.features.freelance.domain.FreelanceCalculator
import com.example.zar.features.freelance.domain.model.FreelanceOverview
import com.example.zar.features.freelance.domain.model.Outstanding
import com.example.zar.features.freelance.domain.model.ProjectStatus
import com.example.zar.features.reminders.domain.ReminderKind
import com.example.zar.features.reminders.domain.ReminderPlanner
import com.example.zar.features.reminders.domain.ReminderPreferences
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {

    private val today = LocalDate.of(2026, 10, 21)
    private val noFreelance = FreelanceOverview(emptyList(), Outstanding(emptyList(), emptyList(), 0))
    private val noBills = BillsOverview(emptyList(), null, emptyList(), emptyList())

    private fun bill(
        id: String = "b1",
        firstDue: LocalDate = today.plusDays(1),
        reminder: BillReminder? = BillReminder.ONE_DAY_BEFORE,
        status: BillStatus = BillStatus.ACTIVE,
    ) = Bill(BillId(id), "Dialog $id", bank.id, lkr(5_850), BillFrequency.MONTHLY, firstDue, status, T0, reminder)

    private fun bills(vararg list: Bill, matches: List<PaymentMatch> = emptyList(), entries: List<com.example.zar.core.domain.model.LedgerEntry> = emptyList()) =
        BillsCalculator.overview(list.toList(), listOf(bank), emptyList(), matches, entries, today)

    private fun plan(bills: BillsOverview = noBills, freelance: FreelanceOverview = noFreelance, prefs: ReminderPreferences = ReminderPreferences()) =
        ReminderPlanner.plan(bills, freelance, prefs, today)

    // --- Bills -----------------------------------------------------------------------------

    @Test
    fun billRemindsFromItsChosenDayUntilTheDueDate() {
        val tomorrow = plan(bills(bill(firstDue = today.plusDays(1)))).single()
        assertEquals(ReminderKind.BILL_DUE, tomorrow.kind)
        assertEquals(1, tomorrow.daysUntilDue)
        assertEquals(lkr(5_850), tomorrow.amount)
        assertEquals(1, plan(bills(bill(firstDue = today))).size)
        assertTrue("Too early for a 1-day reminder", plan(bills(bill(firstDue = today.plusDays(3)))).isEmpty())
        assertEquals(1, plan(bills(bill(firstDue = today.plusDays(7), reminder = BillReminder.ONE_WEEK_BEFORE))).size)
    }

    @Test
    fun noReminderWhenOffStoppedOrAlreadyPaid() {
        assertTrue(plan(bills(bill(reminder = null))).isEmpty())
        assertTrue(plan(bills(bill(status = BillStatus.STOPPED))).isEmpty())

        val b = bill(firstDue = today)
        val due = BillSchedule.expectedPayment(b, today)
        val expense = tx("e1", TransactionType.EXPENSE, lkr(5_850), date = today)
        val paid = BillsCalculator.overview(listOf(b), listOf(bank), listOf(due), listOf(PaymentMatch(due.id, expense.id, lkr(5_850), T0)), listOf(entry(expense)), today)
        assertTrue("A matched due date needs no reminder", plan(paid).isEmpty())
    }

    @Test
    fun aReminderAlreadyShownIsNeverShownAgain() {
        val overview = bills(bill(firstDue = today.plusDays(1)))
        val first = plan(overview).single()
        assertTrue(plan(overview, prefs = ReminderPreferences(notifiedKeys = setOf(first.key))).isEmpty())
    }

    // --- Milestones ------------------------------------------------------------------------

    private fun freelance(vararg milestones: com.example.zar.core.domain.model.ExpectedPayment, status: ProjectStatus = ProjectStatus.ACTIVE) =
        FreelanceCalculator.overview(listOf(project("p1", status = status)), milestones.toList(), emptyList(), emptyList(), today)

    @Test
    fun overdueMilestoneNotifiesOnce() {
        val overview = freelance(milestone("m1", "p1", usd(400), due = today.minusDays(2)), milestone("m2", "p1", usd(100), due = today.plusDays(5)))
        val reminder = plan(freelance = overview).single()
        assertEquals(ReminderKind.MILESTONE_OVERDUE, reminder.kind)
        assertEquals("Project p1", reminder.group)
        assertEquals(usd(400), reminder.amount)
        assertEquals(-2, reminder.daysUntilDue)
        assertTrue(plan(freelance = overview, prefs = ReminderPreferences(notifiedKeys = setOf(reminder.key))).isEmpty())
    }

    @Test
    fun movingTheDueDateAllowsOneMoreAlertForTheNewDate() {
        val old = plan(freelance = freelance(milestone("m1", "p1", usd(400), due = today.minusDays(10)))).single()
        val moved = plan(
            freelance = freelance(milestone("m1", "p1", usd(400), due = today.minusDays(1))),
            prefs = ReminderPreferences(notifiedKeys = setOf(old.key)),
        )
        assertEquals(1, moved.size)
    }

    @Test
    fun noMilestoneAlertsWhenSwitchedOffCancelledOrArchived() {
        val overdue = milestone("m1", "p1", usd(400), due = today.minusDays(2))
        assertTrue(plan(freelance = freelance(overdue), prefs = ReminderPreferences(overdueMilestoneAlerts = false)).isEmpty())
        assertTrue(plan(freelance = freelance(milestone("m1", "p1", usd(400), due = today.minusDays(2), status = ExpectedPaymentStatus.CANCELLED))).isEmpty())
        assertTrue(plan(freelance = freelance(overdue, status = ProjectStatus.ARCHIVED)).isEmpty())
    }

    // --- Bookkeeping and storage -----------------------------------------------------------

    @Test
    fun oldRememberedKeysAreForgotten() {
        val kept = ReminderPlanner.prune(
            setOf(ReminderPlanner.billKey("b1", today.minusDays(200)), ReminderPlanner.billKey("b1", today.minusDays(10)), "odd-key"),
            today,
        )
        assertEquals(setOf(ReminderPlanner.billKey("b1", today.minusDays(10)), "odd-key"), kept)
    }

    @Test
    fun billReminderRoundTripsThroughFirestoreMaps() {
        val b = bill(reminder = BillReminder.THREE_DAYS_BEFORE)
        assertEquals(3L, BillDocumentMapper.toMap(b)["reminderDaysBefore"])
        assertEquals(b, BillDocumentMapper.fromMap(BillDocumentMapper.toMap(b)))
        val off = bill(reminder = null)
        assertTrue("reminderDaysBefore" !in BillDocumentMapper.toMap(off))
        assertEquals(off, BillDocumentMapper.fromMap(BillDocumentMapper.toMap(off)))
    }
}
