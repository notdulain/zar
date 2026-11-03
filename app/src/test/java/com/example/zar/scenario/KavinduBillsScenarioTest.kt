package com.example.zar.scenario

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.bills.domain.BillSchedule
import com.example.zar.features.bills.domain.BillsCalculator
import com.example.zar.features.bills.domain.RecurrenceDetector
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillCancellation
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.BillStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kavindu's committed costs: rent, a gym auto-debited from a second account, and two
 * subscriptions he meant to cancel six months ago (assignment §3.3).
 */
class KavinduBillsScenarioTest {

    private val today = LocalDate.of(2026, 10, 21)
    private val secondary = Account(AccountId("secondary"), "Secondary account", AccountType.BANK, bank.currency,
        OpeningBalance.Unknown, LocalDate.of(2026, 1, 1))

    private fun bill(id: String, name: String, amount: Money, firstDue: LocalDate, account: Account = bank,
        frequency: BillFrequency = BillFrequency.MONTHLY) =
        Bill(BillId(id), name, account.id, amount, frequency, firstDue, BillStatus.ACTIVE, T0)

    private val rent = bill("rent", "Rent", lkr(34_000), LocalDate.of(2026, 1, 1))
    private val gym = bill("gym", "Gym membership", lkr(6_500), LocalDate.of(2026, 1, 31), account = secondary)

    private fun charge(id: String, merchant: String, amount: Long, date: LocalDate) =
        entry(tx(id, TransactionType.EXPENSE, lkr(amount), date = date, spendingClass = SpendingClass.COMMITTED).copy(merchant = merchant))

    @Test
    fun yearlyCostOfHisCommittedBillsIsVisible() {
        val overview = BillsCalculator.overview(listOf(rent, gym), listOf(bank, cash, secondary), emptyList(), emptyList(), emptyList(), today)

        // (34,000 + 6,500) × 12. One number he has never seen before.
        assertEquals(listOf(lkr(486_000)), overview.annual)
        // The gym on the secondary account is due on 31 Oct, a day before rent: the debit he never notices.
        assertEquals(gym.id, overview.next?.billId)
        assertEquals(LocalDate.of(2026, 10, 31), overview.next?.dueDate)
    }

    @Test
    fun gymOnThe31stFallsOnTheLastDayOfShortMonths() {
        val dates = (0..3).map { BillSchedule.dueDate(gym, it) }
        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)), dates)
    }

    @Test
    fun forgottenSubscriptionsAreSuggestedFromHisOwnSpending() {
        val entries = listOf(
            charge("n1", "NETFLIX.COM", 2_643, LocalDate.of(2026, 8, 9)),
            charge("n2", "Netflix com", 2_643, LocalDate.of(2026, 9, 9)),
            charge("s1", "SPOTIFY", 1_299, LocalDate.of(2026, 9, 2)),
            charge("s2", "SPOTIFY", 1_299, LocalDate.of(2026, 10, 2)),
            charge("coffee", "CAFE SEVENTY THREE", 800, LocalDate.of(2026, 10, 3)),
        )
        val suggestions = RecurrenceDetector.suggest(entries, listOf(rent), listOf(bank), emptySet())

        assertEquals("Both subscriptions, and not the one-off coffee", setOf("netflix com", "spotify"),
            suggestions.map { RecurrenceDetector.normalize(it.merchant) }.toSet())
        assertEquals(LocalDate.of(2026, 10, 9), suggestions.single { it.amount == lkr(2_643) }.nextExpected)
    }

    @Test
    fun confirmingACancellationStopsFutureDueDates() {
        val netflix = bill("netflix", "Netflix", lkr(2_643), LocalDate.of(2026, 1, 9))
            .copy(cancellation = BillCancellation.Confirmed(LocalDate.of(2026, 10, 15)))
        val summary = BillsCalculator.overview(listOf(netflix), listOf(bank), emptyList(), emptyList(), emptyList(), today).bills.single()

        assertNull("No next due date once he has really cancelled", summary.next)
    }

    @Test
    fun aChargeAfterCancellingIsFlaggedSoHeCanDispute() {
        val netflix = bill("netflix", "Netflix", lkr(2_643), LocalDate.of(2026, 1, 9))
            .copy(cancellation = BillCancellation.Confirmed(LocalDate.of(2026, 9, 1)))
        val sneaky = charge("late", "NETFLIX.COM", 2_643, LocalDate.of(2026, 10, 9))

        val flagged = BillsCalculator.chargesAfterCancellation(netflix, listOf(sneaky))
        assertTrue(flagged.single().id.value == "late")
    }
}
