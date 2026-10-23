package com.example.zar.features.quicknote.domain.usecase

import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.core.domain.usecase.SaveTransferUseCase
import com.example.zar.features.freelance.FakeExpectedPaymentRepository
import com.example.zar.features.quicknote.domain.review.ProposalInput
import com.example.zar.features.transactions.presentation.activity.FakeAccounts
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmQuickNoteProposalUseCaseTest {
    private val today = LocalDate.of(2026, 10, 23)
    private val accounts = FakeAccounts(listOf(bank, cash))
    private val ledger = FakeLedgerRepository()
    private val expected = FakeExpectedPaymentRepository()
    private val draft = Draft(DraftId("note1"), DraftSource.QUICK_NOTE, emptyList(), DraftStatus.PENDING_REVIEW, T0)

    private fun useCase() = ConfirmQuickNoteProposalUseCase(accounts, ledger, expected, SaveTransactionUseCase(ledger), SaveTransferUseCase(ledger))

    private fun expense(account: String? = "cash") = ProposalInput.Actual(
        TransactionType.EXPENSE, "1250", null, account?.let(::AccountId), today.minusDays(1), "groceries",
        SpendingClass.DISCRETIONARY, "Keells Super", "", null, emptySet(),
    )

    @Test
    fun confirmingAnExpenseSavesItOnceAndNothingElse() = runTest {
        val result = useCase()(draft, 0, expense(), today, T0)

        assertTrue(result.isSuccess)
        assertEquals(listOf("draft-note1-0"), ledger.transactions.keys.map { it.value })
        assertTrue(expected.payments.value.isEmpty())
    }

    @Test
    fun confirmingTheSameProposalAgainStillLeavesOneRecord() = runTest {
        useCase()(draft, 0, expense(), today, T0)
        val again = useCase()(draft, 0, expense(), today, T0.plusSeconds(60))

        assertTrue(again.isSuccess)
        assertEquals(1, ledger.transactions.size)
        assertEquals("The first save is kept as it was", T0, ledger.transactions.values.single().createdAt)
    }

    @Test
    fun twoProposalsOfOneNoteAreSavedAsTwoRecords() = runTest {
        useCase()(draft, 0, expense(), today, T0)
        useCase()(draft, 1, expense(), today, T0)

        assertEquals(setOf("draft-note1-0", "draft-note1-1"), ledger.transactions.keys.map { it.value }.toSet())
    }

    @Test
    fun aProposalThatStillNeedsAnAccountIsNotSaved() = runTest {
        val result = useCase()(draft, 0, expense(account = null), today, T0)

        assertTrue(result.exceptionOrNull() is LedgerError.InvalidRecord)
        assertTrue(ledger.transactions.isEmpty())
    }

    @Test
    fun anExpectedPaymentIsSavedAsAnExpectationAndNeverAsATransaction() = runTest {
        val input = ProposalInput.Expected(Direction.OUT, "4500", null, today.plusDays(5), "Electricity bill", null, emptySet())

        val result = useCase()(draft, 0, input, today, T0)

        assertTrue(result.isSuccess)
        assertTrue(ledger.transactions.isEmpty())
        assertEquals("draft-note1-0", expected.payments.value.single().id.value)
    }

    @Test
    fun aTransferSavesBothLegsAtOnce() = runTest {
        val input = ProposalInput.Transfer(bank.id, cash.id, "3000", null, "", today, emptySet())

        val result = useCase()(draft, 0, input, today, T0)

        assertTrue(result.isSuccess)
        assertEquals(setOf("draft-note1-0-out", "draft-note1-0-in"), ledger.transactions.keys.map { it.value }.toSet())
    }

    @Test
    fun aRefundIsSavedAgainstThePurchaseItWasPointedAt() = runTest {
        ledger.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(2_300), account = bank))
        val input = expense(account = null).copy(type = TransactionType.REFUND, refundOf = TransactionId("p1"), amount = "500")

        val result = useCase()(draft, 0, input, today, T0)

        assertTrue(result.isSuccess)
        assertEquals(TransactionId("p1"), ledger.transactions[TransactionId("draft-note1-0")]?.refundOf)
    }

    @Test
    fun anArchivedAccountCannotReceiveARecord() = runTest {
        accounts.accounts.value = listOf(bank, cash.copy(isArchived = true))

        val result = useCase()(draft, 0, expense(account = "cash"), today, T0)

        assertTrue(result.isFailure)
        assertTrue(ledger.transactions.isEmpty())
    }
}
