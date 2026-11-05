package com.example.zar.features.transactions.domain.manual

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.RateSource
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseFormInput
import com.example.zar.features.transactions.domain.manual.model.ExpenseFormResult
import com.example.zar.features.transactions.domain.manual.model.ExpenseProblem
import com.example.zar.features.transactions.domain.manual.model.TransferFormInput
import com.example.zar.features.transactions.domain.manual.model.TransferFormResult
import com.example.zar.features.transactions.domain.manual.model.TransferProblem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualEntryRulesTest {
    private val today = LocalDate.of(2026, 10, 15)

    private fun expense(
        amount: String = "850",
        account: com.example.zar.core.domain.model.Account? = cash,
        date: LocalDate = today,
        category: ExpenseCategory? = ExpenseCategory.TRANSPORT,
        spendingClass: SpendingClass = SpendingClass.DISCRETIONARY,
        title: String = "Bus to Kandy",
        description: String = "",
    ) = ExpenseFormInput(amount, account, date, category, spendingClass, title, description)

    private fun transfer(
        amount: String = "5,000",
        fee: String = "",
        from: com.example.zar.core.domain.model.Account? = bank,
        to: com.example.zar.core.domain.model.Account? = cash,
        date: LocalDate = today,
        note: String = "",
    ) = TransferFormInput(amount, fee, from, to, date, note)

    private fun problems(result: ExpenseFormResult) = (result as ExpenseFormResult.Invalid).problems
    private fun problems(result: TransferFormResult) = (result as TransferFormResult.Invalid).problems

    // --- Expense validation ---------------------------------------------------------------

    @Test
    fun validExpenseKeepsEveryField() {
        val result = ManualEntryRules.validateExpense(
            expense(amount = "1,250.50", title = "  Keells  ", description = " weekly "),
            today,
        ) as ExpenseFormResult.Valid
        val details = result.details
        assertEquals(125050L, (details.amount as com.example.zar.core.domain.model.money.FiatMoney).minorUnits)
        assertEquals(cash, details.account)
        assertEquals("Keells", details.title)
        assertEquals("weekly", details.description)
        assertEquals(ExpenseCategory.TRANSPORT, details.category)
    }

    @Test
    fun blankTitleAndDescriptionBecomeNull() {
        val details = (ManualEntryRules.validateExpense(expense(title = "  "), today) as ExpenseFormResult.Valid).details
        assertNull(details.title)
        assertNull(details.description)
    }

    @Test
    fun emptyFormListsEveryMissingRequiredField() {
        val result = ManualEntryRules.validateExpense(expense(amount = "", account = null, category = null), today)
        assertEquals(
            setOf(ExpenseProblem.AMOUNT_MISSING, ExpenseProblem.ACCOUNT_MISSING, ExpenseProblem.CATEGORY_MISSING),
            problems(result),
        )
    }

    @Test
    fun amountMustBeAPositiveNumberTheCurrencyCanHold() {
        listOf("0", "-5", "abc", "10.555").forEach { text ->
            assertEquals(
                text,
                setOf(ExpenseProblem.AMOUNT_INVALID),
                problems(ManualEntryRules.validateExpense(expense(amount = text), today)),
            )
        }
    }

    @Test
    fun futureDatesAreRejectedButTodayIsFine() {
        assertEquals(
            setOf(ExpenseProblem.DATE_IN_FUTURE),
            problems(ManualEntryRules.validateExpense(expense(date = today.plusDays(1)), today)),
        )
        assertTrue(ManualEntryRules.validateExpense(expense(date = today), today) is ExpenseFormResult.Valid)
    }

    @Test
    fun textLengthLimitsMatchFirestoreRules() {
        assertTrue(ManualEntryRules.validateExpense(expense(title = "a".repeat(120), description = "b".repeat(500)), today) is ExpenseFormResult.Valid)
        assertEquals(
            setOf(ExpenseProblem.TITLE_TOO_LONG, ExpenseProblem.DESCRIPTION_TOO_LONG),
            problems(ManualEntryRules.validateExpense(expense(title = "a".repeat(121), description = "b".repeat(501)), today)),
        )
    }

    // --- Building the expense -------------------------------------------------------------

    @Test
    fun newExpenseIsAnExpenseWithItsClassCategoryAndTitle() {
        val details = (ManualEntryRules.validateExpense(expense(description = "return trip"), today) as ExpenseFormResult.Valid).details
        val tx = ManualEntryRules.newExpense(TransactionId("e1"), details, T0)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(SpendingClass.DISCRETIONARY, tx.spendingClass)
        assertEquals("transport", tx.categoryId)
        assertEquals("Bus to Kandy", tx.merchant)
        assertEquals("return trip", tx.note)
        assertEquals(cash.id, tx.accountId)
        assertEquals(ReportingConversion.SameCurrency, tx.reportingConversion)
    }

    @Test
    fun selectedExpenseTimeIsDistinctFromItsSaveInstant() {
        val selectedTime = LocalTime.of(8, 35)
        val details = (ManualEntryRules.validateExpense(expense().copy(time = selectedTime), today) as ExpenseFormResult.Valid).details
        val transaction = ManualEntryRules.newExpense(TransactionId("timed"), details, T0)
        assertEquals(selectedTime, transaction.time)
        assertEquals(T0, transaction.createdAt)
    }

    @Test
    fun foreignCurrencyExpenseIsSavedWithAMissingRateNotAGuess() {
        val details = (ManualEntryRules.validateExpense(expense(amount = "12", account = payoneer), today) as ExpenseFormResult.Valid).details
        val tx = ManualEntryRules.newExpense(TransactionId("e2"), details, T0)
        assertEquals(usd(12), tx.amount)
        assertEquals(ReportingConversion.Missing(Currencies.LKR), tx.reportingConversion)
    }

    private fun usdExpense(rate: String, source: RateSource = RateSource.MANUAL, asOf: Instant? = null) =
        expense(amount = "12", account = payoneer).copy(rate = rate, rateSource = source, rateAsOf = asOf)

    @Test
    fun foreignCurrencyExpenseWithATypedRateIsConvertedAndDatedBySaving() {
        val details = (ManualEntryRules.validateExpense(usdExpense("330"), today) as ExpenseFormResult.Valid).details
        val tx = ManualEntryRules.newExpense(TransactionId("e3"), details, T0)
        val conversion = tx.reportingConversion as ReportingConversion.Converted
        assertEquals(lkr(3_960), conversion.amount)
        assertEquals(RateSource.MANUAL, conversion.rate.source)
        assertEquals(T0, conversion.rate.asOf)
    }

    @Test
    fun aProvidersRateKeepsItsSourceAndThePublishedTime() {
        val published = Instant.parse("2026-10-15T00:02:31Z")
        val details = (ManualEntryRules.validateExpense(usdExpense("332.4449", RateSource.PROVIDER, published), today) as ExpenseFormResult.Valid).details
        val conversion = ManualEntryRules.newExpense(TransactionId("e4"), details, T0).reportingConversion as ReportingConversion.Converted
        assertEquals(RateSource.PROVIDER, conversion.rate.source)
        assertEquals(published, conversion.rate.asOf)
        // USD 12 at 332.4449 is LKR 3,989.3388, rounded to the cent.
        assertEquals(com.example.zar.core.domain.model.money.FiatMoney(398_934, Currencies.LKR), conversion.amount)
    }

    @Test
    fun aCommaInTheRateIsADecimalSeparator() {
        val details = (ManualEntryRules.validateExpense(usdExpense("300,5"), today) as ExpenseFormResult.Valid).details
        assertEquals(0, "300.5".toBigDecimal().compareTo(details.rateToReporting))
    }

    @Test
    fun anInvalidRateIsRefusedForAForeignAccountAndIgnoredForLkr() {
        assertEquals(setOf(ExpenseProblem.RATE_INVALID), problems(ManualEntryRules.validateExpense(usdExpense("abc"), today)))
        assertEquals(setOf(ExpenseProblem.RATE_INVALID), problems(ManualEntryRules.validateExpense(usdExpense("0"), today)))
        val lkrExpense = (ManualEntryRules.validateExpense(expense().copy(rate = "abc"), today) as ExpenseFormResult.Valid).details
        assertNull(lkrExpense.rateToReporting)
        assertEquals(ReportingConversion.SameCurrency, ManualEntryRules.newExpense(TransactionId("e5"), lkrExpense, T0).reportingConversion)
    }

    @Test
    fun aBlankRateOnAForeignAccountIsStillMissing() {
        val details = (ManualEntryRules.validateExpense(usdExpense("  "), today) as ExpenseFormResult.Valid).details
        assertNull(details.rateToReporting)
        assertEquals(ReportingConversion.Missing(Currencies.LKR), ManualEntryRules.newExpense(TransactionId("e6"), details, T0).reportingConversion)
    }

    @Test
    fun everyCategoryHasAUniqueStoredIdThatFitsTheRule() {
        val ids = ExpenseCategory.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.length <= 64 })
        assertEquals(ExpenseCategory.GROCERIES, ExpenseCategory.fromId("groceries"))
        assertNull(ExpenseCategory.fromId("nope"))
    }

    // --- Transfer validation --------------------------------------------------------------

    @Test
    fun validTransferWithoutFee() {
        val details = (ManualEntryRules.validateTransfer(transfer(note = " rent top-up "), today) as TransferFormResult.Valid).details
        assertEquals(lkr(5_000), details.amount)
        assertNull(details.fee)
        assertEquals("rent top-up", details.note)
    }

    @Test
    fun blankOrZeroFeeMeansNoFee() {
        listOf("", "  ", "0", "0.00").forEach { fee ->
            val details = (ManualEntryRules.validateTransfer(transfer(fee = fee), today) as TransferFormResult.Valid).details
            assertNull(fee, details.fee)
        }
    }

    @Test
    fun feeMustBeAValidAmount() {
        listOf("abc", "-30", "1.234").forEach { fee ->
            assertEquals(
                fee,
                setOf(TransferProblem.FEE_INVALID),
                problems(ManualEntryRules.validateTransfer(transfer(fee = fee), today)),
            )
        }
        val details = (ManualEntryRules.validateTransfer(transfer(fee = "30"), today) as TransferFormResult.Valid).details
        assertEquals(lkr(30), details.fee)
    }

    @Test
    fun transferNeedsTwoDifferentAccounts() {
        assertEquals(
            setOf(TransferProblem.FROM_MISSING, TransferProblem.TO_MISSING),
            problems(ManualEntryRules.validateTransfer(transfer(from = null, to = null), today)),
        )
        assertEquals(
            setOf(TransferProblem.SAME_ACCOUNT),
            problems(ManualEntryRules.validateTransfer(transfer(from = bank, to = bank), today)),
        )
    }

    @Test
    fun transferBetweenCurrenciesIsNotSupportedYet() {
        assertEquals(
            setOf(TransferProblem.DIFFERENT_CURRENCIES),
            problems(ManualEntryRules.validateTransfer(transfer(to = payoneer), today)),
        )
    }

    @Test
    fun transferAmountDateAndNoteAreChecked() {
        assertEquals(
            setOf(TransferProblem.AMOUNT_MISSING),
            problems(ManualEntryRules.validateTransfer(transfer(amount = ""), today)),
        )
        assertEquals(
            setOf(TransferProblem.AMOUNT_INVALID),
            problems(ManualEntryRules.validateTransfer(transfer(amount = "0"), today)),
        )
        assertEquals(
            setOf(TransferProblem.DATE_IN_FUTURE, TransferProblem.NOTE_TOO_LONG),
            problems(ManualEntryRules.validateTransfer(transfer(date = today.plusDays(2), note = "n".repeat(501)), today)),
        )
    }

    // --- Building the transfer ------------------------------------------------------------

    @Test
    fun newTransferHasBothLegsAndTheSameAmountEitherSide() {
        val details = (ManualEntryRules.validateTransfer(transfer(), today) as TransferFormResult.Valid).details
        val transfer = ManualEntryRules.newTransfer(TransferId("t1"), details, T0)
        assertEquals(lkr(5_000), transfer.sent)
        assertEquals(lkr(5_000), transfer.received)
        assertEquals(TransactionType.TRANSFER_OUT, transfer.outLeg().type)
        assertEquals(TransactionType.TRANSFER_IN, transfer.inLeg().type)
        assertEquals(bank.id, transfer.outLeg().accountId)
        assertEquals(cash.id, transfer.inLeg().accountId)
    }

    @Test
    fun selectedTransferTimeIsOnBothLegsAndItsFee() {
        val selectedTime = LocalTime.of(18, 42)
        val details = (ManualEntryRules.validateTransfer(transfer(fee = "30").copy(time = selectedTime), today) as TransferFormResult.Valid).details
        val transfer = ManualEntryRules.newTransfer(TransferId("timed"), details, T0)
        assertEquals(selectedTime, transfer.time)
        assertEquals(selectedTime, transfer.outLeg().time)
        assertEquals(selectedTime, transfer.inLeg().time)
        assertEquals(selectedTime, transfer.fee?.time)
        assertEquals(T0, transfer.createdAt)
    }

    @Test
    fun feeIsItsOwnCommittedExpenseOnTheSendingAccountWithAStableId() {
        val details = (ManualEntryRules.validateTransfer(transfer(fee = "30"), today) as TransferFormResult.Valid).details
        val fee = ManualEntryRules.newTransferFee(TransferId("t1"), details, T0)!!
        assertEquals(TransactionId("t1-fee"), fee.id)
        assertEquals(TransactionType.EXPENSE, fee.type)
        assertEquals(SpendingClass.COMMITTED, fee.spendingClass)
        assertEquals(bank.id, fee.accountId)
        assertEquals(lkr(30), fee.amount)
        assertEquals(ManualEntryRules.TRANSFER_FEE_CATEGORY_ID, fee.categoryId)
        assertNull(fee.transferId)
    }

    @Test
    fun noFeeMeansNoFeeRecord() {
        val details = (ManualEntryRules.validateTransfer(transfer(), today) as TransferFormResult.Valid).details
        assertNull(ManualEntryRules.newTransferFee(TransferId("t1"), details, T0))
    }
    @Test
    fun amountPlusFeeCannotOverflowTheSendingTotal() {
        val result = ManualEntryRules.validateTransfer(transfer(amount = "92233720368547758.07", fee = "0.01"), today)
        assertEquals(setOf(TransferProblem.FEE_INVALID), problems(result))
    }

    @Test
    fun aCustomCategoryRoundTripsByIdAndNeverInfersASpendingClass() {
        val rentLike = com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory(
            "custom-rent-like", "Rent deposit", "rent deposit", java.time.Instant.EPOCH,
        )
        assertNull(rentLike.usualClass)
        val input = ExpenseFormInput(
            amount = "500", account = com.example.zar.core.domain.TestLedger.cash, date = LocalDate.of(2026, 10, 20),
            category = rentLike, spendingClass = SpendingClass.COMMITTED, title = "Deposit", description = "",
        )
        val details = (ManualEntryRules.validateExpense(input, LocalDate.of(2026, 10, 24)) as ExpenseFormResult.Valid).details
        val tx = ManualEntryRules.newExpense(com.example.zar.core.domain.model.TransactionId("e1"), details, java.time.Instant.EPOCH)
        assertEquals("custom-rent-like", tx.categoryId)
        // The explicitly chosen Counts as is kept; nothing is inferred from the name.
        assertEquals(SpendingClass.COMMITTED, tx.spendingClass)
    }
}
