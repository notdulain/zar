package com.example.zar.features.bills

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.MatchState
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.repository.ExpectedPaymentRepository
import com.example.zar.features.bills.data.repository.BillDocumentMapper
import com.example.zar.features.bills.domain.BillsCalculator
import com.example.zar.features.bills.domain.RecurrenceDetector
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillCancellation
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.BillStatus
import com.example.zar.features.bills.domain.repository.BillRepository
import com.example.zar.features.bills.domain.usecase.ConfirmSuggestionUseCase
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionsTest {

    private val today = LocalDate.of(2026, 10, 21)
    private val sep9 = LocalDate.of(2026, 9, 9)

    private fun charge(id: String, date: LocalDate, merchant: String = "Netflix.com", amount: Money = lkr(2_643), account: Account = bank) =
        entry(tx(id, TransactionType.EXPENSE, amount, account = account, date = date).copy(merchant = merchant))

    private fun suggest(entries: List<LedgerEntry>, bills: List<Bill> = emptyList(), dismissed: Set<String> = emptySet()) =
        RecurrenceDetector.suggest(entries, bills, listOf(bank, cash), dismissed)

    // --- Detection -------------------------------------------------------------------------

    @Test
    fun twoMatchingChargesAMonthApartAreSuggested() {
        val suggestion = suggest(listOf(charge("a", sep9), charge("b", sep9.plusDays(30), merchant = "NETFLIX COM"))).single()
        assertEquals(bank.id, suggestion.accountId)
        assertEquals(lkr(2_643), suggestion.amount)
        assertEquals(sep9.plusDays(30), suggestion.lastDate)
        assertEquals(sep9.plusDays(30).plusMonths(1), suggestion.nextExpected)
        assertEquals(TransactionId("b"), suggestion.lastEntryId)
    }

    @Test
    fun gapMustBe25To35Days() {
        assertEquals(1, suggest(listOf(charge("a", sep9), charge("b", sep9.plusDays(25)))).size)
        assertEquals(1, suggest(listOf(charge("a", sep9), charge("b", sep9.plusDays(35)))).size)
        assertTrue(suggest(listOf(charge("a", sep9), charge("b", sep9.plusDays(24)))).isEmpty())
        assertTrue(suggest(listOf(charge("a", sep9), charge("b", sep9.plusDays(36)))).isEmpty())
    }

    @Test
    fun oneChargeOrAnyMismatchIsNotEnough() {
        assertTrue(suggest(listOf(charge("a", sep9))).isEmpty())
        assertTrue("Different amount", suggest(listOf(charge("a", sep9), charge("b", sep9.plusDays(30), amount = lkr(2_700)))).isEmpty())
        assertTrue("Different account", suggest(listOf(charge("a", sep9), charge("b", sep9.plusDays(30), account = cash))).isEmpty())
        assertTrue("Different currency", suggest(listOf(charge("a", sep9), charge("b", sep9.plusDays(30), amount = usd(2_643)))).isEmpty())
        assertTrue("Different merchant", suggest(listOf(charge("a", sep9), charge("b", sep9.plusDays(30), merchant = "Spotify"))).isEmpty())
        assertTrue("No reliable merchant", suggest(listOf(charge("a", sep9, merchant = "NE"), charge("b", sep9.plusDays(30), merchant = "NE"))).isEmpty())
    }

    @Test
    fun dismissedOrAlreadyTrackedSuggestionsStayHidden() {
        val charges = listOf(charge("a", sep9), charge("b", sep9.plusDays(30)))
        val key = suggest(charges).single().key
        assertTrue(suggest(charges, dismissed = setOf(key)).isEmpty())
        val existing = Bill(BillId("x"), "Netflix com", bank.id, lkr(2_500), BillFrequency.MONTHLY, sep9, BillStatus.ACTIVE, T0)
        assertTrue(suggest(charges, bills = listOf(existing)).isEmpty())
    }

    // --- Cancellation ----------------------------------------------------------------------

    private fun bill(cancellation: BillCancellation? = null) =
        Bill(BillId("b1"), "Netflix.com", bank.id, lkr(2_643), BillFrequency.MONTHLY, LocalDate.of(2026, 9, 9), BillStatus.ACTIVE, T0, cancellation = cancellation)

    @Test
    fun intendingToCancelKeepsDueDatesComing() {
        val summary = BillsCalculator.overview(listOf(bill(BillCancellation.Intended)), listOf(bank), emptyList(), emptyList(), emptyList(), today).bills.single()
        assertEquals(LocalDate.of(2026, 11, 9), summary.next?.dueDate)
        assertTrue(summary.bill.isActive)
    }

    @Test
    fun confirmedCancellationStopsLaterDueDatesButKeepsHistory() {
        val paid = charge("sep", LocalDate.of(2026, 9, 9))
        val b = bill(BillCancellation.Confirmed(LocalDate.of(2026, 10, 1)))
        val overview = BillsCalculator.overview(listOf(b), listOf(bank), emptyList(), emptyList(), listOf(paid), today)
        val summary = overview.bills.single()
        assertNull(summary.next)
        assertEquals(listOf(LocalDate.of(2026, 9, 9)), summary.occurrences.map { it.dueDate })
        assertTrue("No longer in yearly totals", overview.annual.isEmpty())
        assertNull(overview.next)
    }

    @Test
    fun chargesAfterConfirmedCancellationAreFlagged() {
        val b = bill(BillCancellation.Confirmed(LocalDate.of(2026, 10, 1)))
        val flagged = BillsCalculator.chargesAfterCancellation(
            b,
            listOf(
                charge("before", LocalDate.of(2026, 9, 9)),
                charge("after", LocalDate.of(2026, 10, 9), merchant = "NETFLIX.COM LOS GATOS"),
                charge("same-amount", LocalDate.of(2026, 10, 12), merchant = "Unknown"),
                charge("other", LocalDate.of(2026, 10, 15), merchant = "Keells", amount = lkr(900)),
                charge("other-account", LocalDate.of(2026, 10, 9), account = cash),
            ),
        )
        assertEquals(listOf("after", "same-amount"), flagged.map { it.id.value })
        assertTrue("Only an intended cancellation flags nothing", BillsCalculator.chargesAfterCancellation(bill(BillCancellation.Intended), listOf(charge("x", today))).isEmpty())
    }

    @Test
    fun cancellationRoundTripsThroughFirestoreMaps() {
        listOf(null, BillCancellation.Intended, BillCancellation.Confirmed(LocalDate.of(2026, 11, 1))).forEach { cancellation ->
            val b = bill(cancellation)
            assertEquals(b, BillDocumentMapper.fromMap(BillDocumentMapper.toMap(b)))
        }
        assertEquals("2026-11-01", BillDocumentMapper.toMap(bill(BillCancellation.Confirmed(LocalDate.of(2026, 11, 1))))["cancelledFrom"])
    }

    // --- Confirming a suggestion -----------------------------------------------------------

    private class FakeBills : BillRepository {
        val bills = MutableStateFlow<List<Bill>>(emptyList())
        override fun observeBills(): Flow<List<Bill>> = bills
        override suspend fun saveBill(bill: Bill) { bills.value = bills.value.filterNot { it.id == bill.id } + bill }
    }

    private class FakeExpected : ExpectedPaymentRepository {
        val payments = MutableStateFlow<List<ExpectedPayment>>(emptyList())
        val matches = MutableStateFlow<List<PaymentMatch>>(emptyList())
        override fun observeExpectedPayments(): Flow<List<ExpectedPayment>> = payments
        override fun observeMatches(): Flow<List<PaymentMatch>> = matches
        override suspend fun saveExpectedPayment(payment: ExpectedPayment) { payments.value = payments.value.filterNot { it.id == payment.id } + payment }
        override suspend fun saveMatch(match: PaymentMatch) { if (matches.value.none { it.id == match.id }) matches.value = matches.value + match }
        override suspend fun removeMatch(expectedPaymentId: ExpectedPaymentId, transactionId: TransactionId) = Unit
    }

    @Test
    fun confirmingCreatesOneMonthlyBillWithTheLatestChargeLinked() = runBlocking {
        val charges = listOf(charge("a", sep9), charge("b", sep9.plusDays(30)))
        val suggestion = suggest(charges).single()
        val bills = FakeBills()
        val expected = FakeExpected()
        val confirm = ConfirmSuggestionUseCase(bills, expected)

        confirm(suggestion, T0)
        confirm(suggestion, T0)

        val created = bills.bills.value.single()
        assertEquals(BillFrequency.MONTHLY, created.frequency)
        assertEquals(suggestion.lastDate, created.firstDue)
        assertEquals(1, expected.matches.value.size)

        val summary = BillsCalculator.overview(listOf(created), listOf(bank), expected.payments.value, expected.matches.value, charges, today).bills.single()
        assertEquals(MatchState.MATCHED, summary.occurrences.first { it.dueDate == suggestion.lastDate }.progress.state)
        assertTrue("Now tracked, so no longer suggested", suggest(charges, bills = listOf(created)).isEmpty())
    }
}
